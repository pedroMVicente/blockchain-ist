package pt.tecnico.blockchainist.node.grpc;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.google.protobuf.InvalidProtocolBufferException;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.key.KeyManager;
import pt.tecnico.blockchainist.common.transaction.InternalTransaction;
import pt.tecnico.blockchainist.common.transaction.TransactionConverter;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.EncryptedRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.SignedCreateWalletRequest;
import pt.tecnico.blockchainist.contract.SignedDeleteWalletRequest;
import pt.tecnico.blockchainist.contract.SignedTransaction;
import pt.tecnico.blockchainist.contract.SignedTransferRequest;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;


/**
 * gRPC service implementation for node operations.
 *
 * <p>This class handles client-facing RPCs, validates incoming requests,
 * delegates state-changing operations to the transactional executor, and
 * serves read-only operations directly from local node state.
 */
public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {
    private static final String CLASSNAME = NodeServiceImpl.class.getSimpleName();
    private static final String CLIENT_SIGNATURE_PREFIX = "clients/signatures/public";
    private static final String CLIENT_ENCRYPTION_PREFIX = "clients/encryption/secret";

    private final NodeState state;
    private final ClientSequencerService clientSequencer;
    private final Thread fetcherThread;
    private final NodeRequestValidator validator;
    private final NodeErrorMapper errorMapper;
    private final NodeTransactionalRequestExecutor transactionalExecutor;

    /**
     * Creates a node gRPC service bound to the given local state and sequencer endpoint.
     *
     * @param state the local node state
     * @param host the sequencer host
     * @param port the sequencer port
     */
    public NodeServiceImpl(
        NodeState state,
        String host,
        int port
    ) {
        this.state = state;
        this.clientSequencer = new ClientSequencerService(host, port);
        this.validator = new NodeRequestValidator();
        this.errorMapper = new NodeErrorMapper();
        this.transactionalExecutor =
            new NodeTransactionalRequestExecutor(
                state,
                clientSequencer,
                errorMapper
            );

        BlockFetcher fetcher = new BlockFetcher(clientSequencer, state);
        this.fetcherThread = new Thread(fetcher, "block-fetcher");
        this.fetcherThread.setDaemon(true);
        this.fetcherThread.start();
    }


    /**
     * Receives a create wallet RPC, validates it, converts it to a transaction,
     * and delegates execution through the sequencer.
     *
     * @param request the create wallet request
     * @param responseObserver the observer used to return the final response
     */
    @Override
    public void createWallet(
        EncryptedRequest encryptedRequest,
        StreamObserver<CreateWalletResponse> responseObserver
    ) {
        // Decrypt message
        byte[] decryptedBytes = KeyManager.decrypt(
            getUserAESKey(encryptedRequest.getUserId()),
            encryptedRequest.getIv().toByteArray(),
            encryptedRequest.getEncryptedPayload().toByteArray()
        );

        // Parse the decrypted bytes
        SignedCreateWalletRequest signedRequest = null;
        try {
            signedRequest = SignedCreateWalletRequest.parseFrom(decryptedBytes);
        } catch (InvalidProtocolBufferException e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.InternalErrorException)
                .asRuntimeException());
            return;
        }

        // Verify the signature
        CreateWalletRequest request = signedRequest.getRequest();

        if(!KeyManager.verifySignature(
            getUserPublicKey(getClass().getClassLoader() ,request.getUserId()),
            request.toByteArray(),
            signedRequest.getSignature().toByteArray()
        )){
            responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription(ErrorMessage.UnauthorisedUserSignatureException)
                .asRuntimeException());
            return;
        }

        DebugLog.log(
            CLASSNAME,
            LogMessage.createWalletReceived(
                request.getRequestId(),
                request.getUserId(),
                request.getWalletId()
            )
        );

        // Validate if the transaction is valid
        if (!validator.validateCreateWalletRequest(request, responseObserver)) {
            return;
        }

        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
            .setCreateWallet(signedRequest)
            .build();

        transactionalExecutor.executeCreateWallet(
            signedTransaction, responseObserver
        );
    }


    /**
     * Receives a delete wallet RPC, validates it, converts it to a transaction,
     * and delegates execution through the sequencer.
     *
     * @param request the delete wallet request
     * @param responseObserver the observer used to return the final response
     */
    @Override
    public void deleteWallet(
        EncryptedRequest encryptedRequest,
        StreamObserver<DeleteWalletResponse> responseObserver
    ) {

         // Decrypt message
        byte[] decryptedBytes = KeyManager.decrypt(
            getUserAESKey(encryptedRequest.getUserId()),
            encryptedRequest.getIv().toByteArray(),
            encryptedRequest.getEncryptedPayload().toByteArray()
        );

        // Parse the decrypted bytes
        SignedDeleteWalletRequest signedRequest = null;
        try {
            signedRequest = SignedDeleteWalletRequest.parseFrom(decryptedBytes);
        } catch (InvalidProtocolBufferException e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.InternalErrorException)
                .asRuntimeException());
            return;
        }

        // Verify the signature
        DeleteWalletRequest request = signedRequest.getRequest();

        if(!KeyManager.verifySignature(
            getUserPublicKey(getClass().getClassLoader() ,request.getUserId()),
            request.toByteArray(),
            signedRequest.getSignature().toByteArray()
        )){
            responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription(ErrorMessage.UnauthorisedUserSignatureException)
                .asRuntimeException());
            return;
        }

        DebugLog.log(CLASSNAME, LogMessage.deleteWalletReceived(
                request.getRequestId(),
                request.getUserId(),
                request.getWalletId()
            )
        );

        // Validate if the transaction is valid
        if (!validator.validateDeleteWalletRequest(request, responseObserver)) {
            return;
        }

        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
            .setDeleteWallet(signedRequest)
            .build();

        transactionalExecutor.executeDeleteWallet(
            signedTransaction, responseObserver
        );
    }


    /**
     * Receives a transfer RPC, validates it, converts it to a transaction,
     * and delegates execution through the sequencer.
     *
     * @param request the transfer request
     * @param responseObserver the observer used to return the final response
     */
    @Override
    public void transfer(
        EncryptedRequest encryptedRequest,
        StreamObserver<TransferResponse> responseObserver
    ) {
         // Decrypt message
        byte[] decryptedBytes = KeyManager.decrypt(
            getUserAESKey(encryptedRequest.getUserId()),
            encryptedRequest.getIv().toByteArray(),
            encryptedRequest.getEncryptedPayload().toByteArray()
        );

        // Parse the decrypted bytes
        SignedTransferRequest signedRequest = null;
        try {
            signedRequest = SignedTransferRequest.parseFrom(decryptedBytes);
        } catch (InvalidProtocolBufferException e) {
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.InternalErrorException)
                .asRuntimeException());
            return;
        }

        // Verify the signature
        TransferRequest request = signedRequest.getRequest();

        if(!KeyManager.verifySignature(
            getUserPublicKey(getClass().getClassLoader() ,request.getSrcUserId()),
            request.toByteArray(),
            signedRequest.getSignature().toByteArray()
        )){
            responseObserver.onError(Status.PERMISSION_DENIED
                .withDescription(ErrorMessage.UnauthorisedUserSignatureException)
                .asRuntimeException());
            return;
        }

        DebugLog.log(
            CLASSNAME, LogMessage.transferReceived(
                request.getRequestId(),
                request.getSrcUserId(),
                request.getSrcWalletId(),
                request.getDstWalletId(),
                request.getValue()
            )
        );

        // Validate if the transaction is valid
        if (!validator.validateTransferRequest(request, responseObserver)) {
            return;
        }

        SignedTransaction signedTransaction = SignedTransaction.newBuilder()
            .setTransfer(signedRequest)
            .build();

        transactionalExecutor.executeTransfer(
            signedTransaction, responseObserver
        );
    }


    /**
     * Returns the current balance of a wallet from local node state.
     *
     * @param request the read balance request
     * @param responseObserver the observer used to return the final response
     */
    @Override
    public void readBalance(
        ReadBalanceRequest request,
        StreamObserver<ReadBalanceResponse> responseObserver
    ) {
        DebugLog.log(CLASSNAME, LogMessage.readBalanceReceived(request.getWalletId()));

        try {
            if (!state.isBootstrapping()){
                state.waitIfNotFullyInitialized();
            }
        } catch (InterruptedException e) {
            DebugLog.log(CLASSNAME, LogMessage.ReadBalanceInterruptedWhileWaitingForInitializationMessage);
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.InterruptedWhileWaitingForNodeInitializationMessage)
                .asRuntimeException());
            return;
        }

        if (!validator.validateReadBalanceRequest(request, responseObserver)) {
            return;
        }

        String walletId = request.getWalletId().trim();

        try {
            long balance = state.readBalance(walletId);

            responseObserver.onNext(ReadBalanceResponse.newBuilder()
                .setBalance(balance)
                .build());
            responseObserver.onCompleted();
            DebugLog.log(CLASSNAME,
                LogMessage.readBalanceResponseReturned(walletId, balance));

        } catch (final WalletDoesNotExistException e) {
            DebugLog.log(CLASSNAME,
                    LogMessage.readBalanceDomainError(walletId, e.getMessage()));
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }


    /**
     * Returns the list of transactions that this node has already applied locally.
     *
     * @param request the blockchain-state request
     * @param responseObserver the observer used to return the final response
     */
    @Override
    public void getBlockchainState(
        GetBlockchainStateRequest request,
        StreamObserver<GetBlockchainStateResponse> responseObserver
    ) {
        DebugLog.log(CLASSNAME, LogMessage.GetBlockchainStateReceivedMessage);

        try {
            if (!state.isBootstrapping()){
                state.waitIfNotFullyInitialized();
            }
        } catch (InterruptedException e) {
            DebugLog.log(CLASSNAME, LogMessage.GetBlockchainStateInterruptedWhileWaitingForInitializationMessage);
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.INTERNAL
                .withDescription(ErrorMessage.InterruptedWhileWaitingForNodeInitializationMessage)
                .asRuntimeException());
            return;
        }

        List<InternalTransaction> blockchain = state.getBlockchainState();

        responseObserver.onNext(GetBlockchainStateResponse.newBuilder()
            .addAllTransactions(blockchain.stream()
                .map(TransactionConverter::convertToTransaction)
                .toList())
            .build());
        responseObserver.onCompleted();
        DebugLog.log(CLASSNAME, LogMessage.getBlockchainStateResponseReturned(blockchain.size()));
    }


    /**
     * Shuts down background services started by the node gRPC service.
     *
     * @throws InterruptedException if shutdown waits are interrupted
     */
    public void shutdown() throws InterruptedException {
        fetcherThread.interrupt();
        clientSequencer.shutdown();
        fetcherThread.join();
    }

    static PublicKey getUserPublicKey(
        ClassLoader classLoader,
        String user
    ) {
        if (user == null || user.contains("..") || user.contains("/") || user.contains("\\")) {
            DebugLog.log(CLASSNAME, LogMessage.rejectedInvalidUserIdentifier(user));
            return null;
        }

        PublicKey publicKey = null;
        try {
            publicKey = KeyManager.loadPublicKey(
                classLoader,
                CLIENT_SIGNATURE_PREFIX + user + KeyManager.RSA_KEY_EXTENSION
            );
        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.rsaKeyFactoryUnavailable(e.getMessage()));
        } catch (InvalidKeySpecException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidOrMalformedRsaPublicKeyForUser(user, e.getMessage()));
        } catch (IOException e) {
            DebugLog.log(CLASSNAME, LogMessage.failedToReadPublicKeyForUser(user, e.getMessage()));
        }
        return publicKey;
    }

    private SecretKeySpec getUserAESKey(
        String user
    ){
        if (user == null || user.contains("..") || user.contains("/") || user.contains("\\")) {
            DebugLog.log(CLASSNAME, LogMessage.rejectedInvalidUserIdentifier(user));
            return null;
        }

        return KeyManager.loadAESKey(
            getClass().getClassLoader(),
            CLIENT_ENCRYPTION_PREFIX + user + KeyManager.AES_KEY_EXTENSION
        );
    }
}