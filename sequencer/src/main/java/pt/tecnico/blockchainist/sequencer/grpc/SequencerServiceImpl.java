package pt.tecnico.blockchainist.sequencer.grpc;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.key.KeyManager;
import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;
import pt.tecnico.blockchainist.common.transaction.TransactionConverter;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.BlockRequest;
import pt.tecnico.blockchainist.contract.BlockResponse;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.BroadcastResponse;
import pt.tecnico.blockchainist.contract.EncryptedRequest;
import pt.tecnico.blockchainist.contract.EncryptedResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.IsStartingUpRequest;
import pt.tecnico.blockchainist.contract.IsStartingUpResponse;
import pt.tecnico.blockchainist.sequencer.domain.block.SequencerBlock;
import pt.tecnico.blockchainist.sequencer.domain.block.SequencerBlockConverter;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;
import pt.tecnico.blockchainist.sequencer.domain.message.LogMessage;
import pt.tecnico.blockchainist.sequencer.domain.message.ErrorMessage;


/**
 * gRPC service implementation exposing the sequencer's broadcast and
 * block-delivery operations to node clients.
 *
 * <p>This service handles three RPCs:
 * <ul>
 *   <li>{@link #broadcast} – accepts a transaction from a node and enqueues it
 *       for inclusion in the next block</li>
 *   <li>{@link #deliverBlock} – returns a requested block, signed with the
 *       sequencer's RSA private key so nodes can verify its authenticity</li>
 * </ul>
 *
 * <p>Every delivered block is signed with an RSA/SHA-256 signature over its
 * raw protobuf bytes. If the private key fails to load at construction time,
 * {@link #signBlock} will return {@code null} and block delivery will fail for
 * every subsequent request.
 */
public class SequencerServiceImpl extends SequencerServiceGrpc.SequencerServiceImplBase {

    private static final String CLASSNAME = SequencerServiceImpl.class.getSimpleName();

    private final SequencerState sequencerState;

    /** RSA private key used to sign each outgoing block. */
    private PrivateKey privateKey;

    private SecretKeySpec sequencerAESkey;

    /**
     * Creates the gRPC sequencer service, backed by the given sequencer state,
     * and loads the RSA private key used to sign delivered blocks.
     *
     * <p>If the private key cannot be loaded, the failure is logged and
     * {@link #privateKey} remains {@code null}. All subsequent calls to
     * {@link #deliverBlock} will then fail because {@link #signBlock} cannot
     * produce a valid signature.
     *
     * @param sequencerState the shared sequencer state used by all RPC handlers
     */
    public SequencerServiceImpl(SequencerState sequencerState) {
        this.sequencerState = sequencerState;

        try {
            this.privateKey = KeyManager.loadPrivateKey(
                getClass().getClassLoader(),
                "signature/privateSequencer.der"
            );

        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.rsaKeyFactoryUnavailable(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.FailedToLoadSequencerPrivateKeyMessage, e);

        } catch (InvalidKeySpecException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidOrMalformedSequencerPrivateKey(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.FailedToLoadSequencerPrivateKeyMessage, e);

        } catch (IOException e) {
            DebugLog.log(CLASSNAME, LogMessage.failedToReadSequencerPrivateKey(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.FailedToLoadSequencerPrivateKeyMessage, e);
        }

        this.sequencerAESkey = KeyManager.loadAESKey(
            getClass().getClassLoader(),
            "encryption/secretSequencer.key"
        );

        if (this.sequencerAESkey == null) {
            throw new IllegalStateException(
                ErrorMessage.FailedToLoadSequencerAESKeyMessage("encryption/secretSequencer.key")
            );
        }
    }

    /**
     * Accepts a transaction broadcast from a node and enqueues it in the
     * sequencer for inclusion in the next block.
     *
     * <p>The call fails with:
     * <ul>
     *   <li>{@link Status#ABORTED} if the request ID has already been seen
     *       (duplicate transaction)</li>
     *   <li>{@link Status#UNAVAILABLE} if the sequencer is not currently
     *       accepting transactions (e.g. shutting down)</li>
     *   <li>{@link Status#INTERNAL} for any other unexpected error</li>
     * </ul>
     *
     * @param request          the broadcast request containing the serialised transaction
     * @param responseObserver the observer used to return the result or an error to the caller
     */
    @Override
    public void broadcast(
        EncryptedRequest encryptedRequest,
        StreamObserver<BroadcastResponse> responseObserver
    ) {
        try {

             // Decrypt message
            byte[] decryptedBytes = KeyManager.decrypt(
                this.sequencerAESkey,
                encryptedRequest.getIv().toByteArray(),
                encryptedRequest.getEncryptedPayload().toByteArray()
            );

            // Parse the decrypted bytes
            BroadcastRequest request = BroadcastRequest.parseFrom(decryptedBytes);

            SignedInternalTransaction signedInternalTransaction =
                TransactionConverter.convertToSignedInternalTransaction(request.getTransaction());

            List<Long> dependsOn = request.getDependsOnList();
            boolean accepted = sequencerState.addTransaction(signedInternalTransaction, dependsOn);
            if (!accepted) {
                DebugLog.log(CLASSNAME, LogMessage.duplicatedTransactionRequestId(
                    signedInternalTransaction.getInternalTransaction().getRequestId())
                );
                responseObserver.onError(Status.ABORTED
                    .withDescription(
                        ErrorMessage.duplicatedTransactionRequestId(
                            signedInternalTransaction.getInternalTransaction().getRequestId()
                        )
                    )
                    .asRuntimeException());
                return;
            }

            DebugLog.log(CLASSNAME, LogMessage.transactionAccepted(signedInternalTransaction.getInternalTransaction().getRequestId()));

            responseObserver.onNext(BroadcastResponse.newBuilder().build());
            responseObserver.onCompleted();
            DebugLog.log(CLASSNAME, LogMessage.BroadcastResponseReturnedMessage);

        } catch (IllegalStateException e) {
            DebugLog.log(CLASSNAME, LogMessage.broadcastRejectedSequencerUnavailable(e.getMessage()));
            responseObserver.onError(Status.UNAVAILABLE
                .withDescription(e.getMessage())
                .asRuntimeException());

        } catch (IllegalArgumentException e) {
            DebugLog.log(CLASSNAME, LogMessage.broadcastFailedBadTransaction(e.getMessage()));
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.broadcastFailed(e.getMessage()))
                .asRuntimeException());

        } catch (RuntimeException e) {
            DebugLog.log(
                CLASSNAME,
                LogMessage.broadcastFailedUnexpectedly(
                    e.getClass().getSimpleName(),
                    e.getMessage()
                )
            );
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.BroadcastInternalServerErrorMessage)
                .asRuntimeException());
        } catch (InvalidProtocolBufferException e) {
            DebugLog.log(CLASSNAME, LogMessage.broadcastInvalidProtobufPayload(e.getMessage()));
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(ErrorMessage.BroadcastInvalidProtobufPayloadMessage)
                    .asRuntimeException()
            );
        }
    }

    /**
     * Delivers the block at the requested block number, signed with the
     * sequencer's RSA private key.
     *
     * <p>If the requested block has not yet been sealed, this call blocks until
     * it becomes available. The returned {@link BlockResponse} contains the
     * serialised block and a detached RSA/SHA-256 signature over its raw
     * protobuf bytes, which the receiving node must verify before applying it.
     *
     * <p>The call fails with:
     * <ul>
     *   <li>{@link Status#UNAVAILABLE} if the sequencer is shutting down and
     *       the block will never be produced</li>
     *   <li>{@link Status#CANCELLED} if the server thread is interrupted while
     *       waiting for the block to be sealed</li>
     *   <li>{@link Status#INTERNAL} for any other unexpected error</li>
     * </ul>
     *
     * @param request          the block request specifying the desired block number
     * @param responseObserver the observer used to return the signed block or an error
     */
    @Override
    public void deliverBlock(
        EncryptedRequest encryptedRequest,
        StreamObserver<EncryptedResponse> responseObserver
    ) {
        try {

             // Decrypt message
            byte[] decryptedBytes = KeyManager.decrypt(
                this.sequencerAESkey,
                encryptedRequest.getIv().toByteArray(),
                encryptedRequest.getEncryptedPayload().toByteArray()
            );

            // Parse the decrypted bytes
            BlockRequest request = BlockRequest.parseFrom(decryptedBytes);

            int requestedBlockNumber = request.getBlockNumber();
            DebugLog.log(CLASSNAME, LogMessage.deliverBlockReceivedRequest(requestedBlockNumber));

            SequencerBlock block = sequencerState.getBlock(requestedBlockNumber);
            boolean isLatestBlockClosed = requestedBlockNumber == (sequencerState.getClosedBlocksCount() - 1);

            Block blockGRPC = SequencerBlockConverter.convertToBlock(block);
            byte[] sig = signBlock(blockGRPC);

            BlockResponse response = BlockResponse.newBuilder()
                .setBlock(blockGRPC)
                .setIsLatestBlock(isLatestBlockClosed)
                .setSignature(ByteString.copyFrom(sig))
                .build();

            byte[] iv = KeyManager.generateIV();
            EncryptedResponse encryptedResponse = EncryptedResponse.newBuilder()
                .setEncryptedPayload(ByteString.copyFrom(
                    KeyManager.encrypt(this.sequencerAESkey, iv, response.toByteArray())
                ))
                .setIv(ByteString.copyFrom(iv))
                .build();

            responseObserver.onNext(encryptedResponse);
            responseObserver.onCompleted();
            DebugLog.log(CLASSNAME, LogMessage.deliverBlockResponseReturned(block.getBlockNumber()));

        } catch (IllegalStateException e) {
            DebugLog.log(CLASSNAME, LogMessage.deliverBlockAbortedSequencerUnavailable(e.getMessage()));
            responseObserver.onError(Status.UNAVAILABLE
                .withDescription(e.getMessage())
                .asRuntimeException());

        } catch (InterruptedException e) {
            DebugLog.log(CLASSNAME, LogMessage.deliverBlockInterruptedWhileWaiting(e.getMessage()));
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED
                .withDescription(ErrorMessage.OperationCancelledMessage)
                .asRuntimeException());

        } catch (RuntimeException e) {
            DebugLog.log(
                CLASSNAME,
                LogMessage.deliverBlockFailedUnexpectedly(e.getClass().getSimpleName(), e.getMessage())
            );
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.DeliverBlockInternalServerErrorMessage)
                .asRuntimeException());

        } catch (InvalidProtocolBufferException e) {
            DebugLog.log(
                CLASSNAME,
                LogMessage.deliverBlockInvalidProtobufPayload(e.getMessage())
            );
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(ErrorMessage.DeliverBlockInvalidProtobufPayloadMessage)
                    .asRuntimeException()
            );
        }
    }


    /**
     * Returns whether the sequencer is starting up.
     *
     * @param request          the request for checking the startup status
     * @param responseObserver the observer used to return the startup status or an error
     */
    @Override
    public void isStartingUp(
        IsStartingUpRequest request,
        StreamObserver<IsStartingUpResponse> responseObserver
    ) {
        responseObserver.onNext(IsStartingUpResponse.newBuilder()
            .setIsStartingUp(sequencerState.isStartingUp())
            .build());
        responseObserver.onCompleted();
    }

    /**
     * Propagates a shutdown signal to the underlying {@link SequencerState},
     * causing any threads blocked inside {@link #deliverBlock} to be unblocked
     * and return an {@link Status#UNAVAILABLE} error.
     */
    public void shutdown() {
        sequencerState.shutdown();
    }

    /**
     * Signs the raw protobuf bytes of a block using the sequencer's RSA private
     * key and the SHA256withRSA algorithm.
     *
     * <p>Returns {@code null} if signing fails for any cryptographic reason.
     * Callers that pass a {@code null} signature to {@link ByteString#copyFrom}
     * will encounter a {@link NullPointerException}, which will propagate up as
     * an {@link Status#INTERNAL} gRPC error in {@link #deliverBlock}.
     *
     * @param block the protobuf block whose bytes are to be signed
     * @return the RSA signature bytes, or {@code null} if a cryptographic error
     *         prevents signing
     */
    private byte[] signBlock(Block block) {
        byte[] signatureBytes = null;
        try {
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(this.privateKey);
            sig.update(block.toByteArray());
            signatureBytes = sig.sign();
            return signatureBytes;


        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.blockSigningAlgorithmUnavailable(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.BlockSigningFailedMessage, e);

        } catch (InvalidKeyException e) {
            DebugLog.log(CLASSNAME, LogMessage.blockSigningInvalidKey(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.BlockSigningFailedMessage, e);

        } catch (SignatureException e) {
            DebugLog.log(CLASSNAME, LogMessage.blockSigningInvalidState(e.getMessage()));
            throw new IllegalStateException(ErrorMessage.BlockSigningFailedMessage, e);
        }
    }
}