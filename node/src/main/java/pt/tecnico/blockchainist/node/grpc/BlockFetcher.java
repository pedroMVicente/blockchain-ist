package pt.tecnico.blockchainist.node.grpc;

import io.grpc.StatusRuntimeException;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.key.KeyManager;
import pt.tecnico.blockchainist.common.transaction.CreateWalletTransaction;
import pt.tecnico.blockchainist.common.transaction.DeleteWalletTransaction;
import pt.tecnico.blockchainist.common.transaction.InternalTransaction;
import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;
import pt.tecnico.blockchainist.common.transaction.TransferTransaction;
import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.BlockResponse;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.domain.block.NodeBlock;
import pt.tecnico.blockchainist.node.domain.block.NodeBlockConverter;
import pt.tecnico.blockchainist.node.domain.RequestOutcome;
import pt.tecnico.blockchainist.node.domain.exceptions.BalanceIsNotZeroException;
import pt.tecnico.blockchainist.node.domain.exceptions.DestinationWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.IncorrectOwnerException;
import pt.tecnico.blockchainist.node.domain.exceptions.NonPositiveTransferAmountException;
import pt.tecnico.blockchainist.node.domain.exceptions.SourceWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.SparseBalanceFromOriginException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletAlreadyExistsException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Background worker that continuously fetches ordered blocks from the sequencer,
 * applies their transactions to local node state, and records the canonical
 * outcome of each request.
 *
 * <p>On construction, the fetcher catches up with all blocks already closed by the
 * sequencer before marking the node as ready to serve requests. Afterwards, it runs
 * in a dedicated thread and processes new blocks as they are produced.
 *
 * <p>Block integrity is enforced via an RSA/SHA-256 signature produced by the
 * sequencer. Blocks whose signature cannot be verified are silently dropped.
 *
 * <p>Sequencer unavailability is handled with a fixed back-off retry of
 * {@value #FETCH_RETRY_DELAY_MS} ms between attempts.
 */
public class BlockFetcher implements Runnable {

    private static final String CLASSNAME = BlockFetcher.class.getSimpleName();

    /** Milliseconds to wait before retrying after a failed sequencer call. */
    private static final int FETCH_RETRY_DELAY_MS = 500;

    /** The gRPC client used to communicate with the sequencer. */
    private final ClientSequencerService clientSequencer;

    /** The local node state that transactions are applied to. */
    private final NodeState state;

    /** RSA public key used to verify block signatures produced by the sequencer. */
    private PublicKey publicKey;

    /**
     * Pairs a request identifier with the canonical outcome produced when its
     * enclosing transaction was applied to node state.
     *
     * <p>Results are collected during block application and committed to
     * {@link NodeState} only after the block itself has been recorded, so that
     * waiting clients are never unblocked before the block is durable.
     */
    private static class TxResult {

        private final long requestId;
        private final RequestOutcome outcome;

        /**
         * Creates a result entry for one applied request.
         *
         * @param requestId the unique identifier of the request
         * @param outcome   the outcome produced when applying the request's transaction
         */
        private TxResult(long requestId, RequestOutcome outcome) {
            this.requestId = requestId;
            this.outcome = outcome;
        }
    }

    /**
     * Creates a {@code BlockFetcher}, loads the sequencer's public key, and
     * replays all blocks that were already closed before this node started.
     *
     * <p>After replay, {@link NodeState#markFullyInitializedIfNotYet()} is called so
     * that the node can begin accepting client requests with a fully up-to-date state.
     *
     * <p>If the sequencer public key cannot be loaded, signature verification
     * will fail for every subsequent block and those blocks will be dropped.
     * The specific cause is logged at construction time.
     *
     * @param clientSequencer the gRPC client used to communicate with the sequencer
     * @param state           the local node state that transactions are applied to
     */
    public BlockFetcher(
        ClientSequencerService clientSequencer,
        NodeState state
    ) {
        this.clientSequencer = clientSequencer;
        this.state = state;

        this.publicKey = null;
        try {
            this.publicKey = KeyManager.loadPublicKey(
                getClass().getClassLoader(),
                "sequencer/signature/publicSequencer.der"
            );
            DebugLog.log(CLASSNAME, LogMessage.PublicKeyLoadedSuccessfully);

        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.rsaKeyFactoryUnavailable(e.getMessage()));

        } catch (InvalidKeySpecException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidOrMalformedSequencerPublicKey(e.getMessage()));

        } catch (IOException e) {
            DebugLog.log(CLASSNAME, LogMessage.failedToReadSequencerPublicKey(e.getMessage()));
        }
    }

    /**
     * Continuously fetches and applies the next expected block until the thread
     * is interrupted.
     *
     * <p>Unexpected {@link RuntimeException}s are caught and logged so that a
     * single bad block cannot terminate the background thread.
     */
    @Override
    public void run() {

        state.setSequencerJustStarted(this.clientSequencer.isStartingUp());

        while (!Thread.currentThread().isInterrupted()) {
            try {
                tryFetchAndApplyNextBlock();
            } catch (RuntimeException e) {
                DebugLog.log(CLASSNAME, LogMessage.unexpectedErrorWhileProcessingBlock(e.getMessage()));
            }
        }
    }

    /**
     * Fetches the next block expected by this node, verifies its signature,
     * applies each of its transactions in order, records the block as processed,
     * and stores the canonical outcome for every completed request.
     *
     * <p>Transactions whose request has already been completed (e.g. because the
     * node is catching up after a restart) are skipped without re-applying them.
     *
     * <p>Outcomes are committed to {@link NodeState} only <em>after</em> the block
     * has been durably recorded, so that a client waiting on a request is never
     * unblocked before the block is persisted.
     *
     * <p>If the sequencer returns a gRPC error, the fetch is retried after
     * {@value #FETCH_RETRY_DELAY_MS} ms. If the retry sleep is itself interrupted,
     * the thread's interrupt flag is restored and the loop in {@link #run()} will
     * exit on the next iteration.
     */
    private void tryFetchAndApplyNextBlock() {
        int nextBlockNumber = state.getNextBlockNumber();
        List<TxResult> results = new ArrayList<>();

        try {
            BlockResponse response = clientSequencer.deliverBlock(nextBlockNumber);

            boolean isValid = verifySignature(response);
            if (!isValid) {
                DebugLog.log(CLASSNAME,
                     LogMessage.droppingBlockInvalidSignature(nextBlockNumber));
                return;
            }

            boolean isLatestBlock = response.getIsLatestBlock();
            NodeBlock block = NodeBlockConverter.convertToNodeBlock(response.getBlock());

            if (block.getBlockNumber() != nextBlockNumber) {
                throw new IllegalStateException(
                    ErrorMessage.unexpectedBlockNumber(nextBlockNumber, block.getBlockNumber())
                );
            }

            DebugLog.log(CLASSNAME,
                LogMessage.receivedBlock(block.getBlockNumber(), block.getSignedTransactions().size()));

            for (SignedInternalTransaction signedTx : block.getSignedTransactions()) {
                long requestId = signedTx.getInternalTransaction().getRequestId();

                if (state.hasCompletedRequest(requestId)) {
                    DebugLog.log(CLASSNAME, LogMessage.skippingAlreadyCompletedRequest(requestId));
                    continue;
                }

                RequestOutcome outcome = processTransaction(signedTx);
                results.add(new TxResult(requestId, outcome));
            }

            state.recordProcessedBlock(block);

            for (TxResult txResult : results) {
                state.recordCompletedRequest(txResult.requestId, txResult.outcome);
            }

            if (isLatestBlock) {
                state.markFullyInitializedIfNotYet();
            }

        } catch (StatusRuntimeException e) {
            DebugLog.log(CLASSNAME,
                LogMessage.grpcErrorFetchingBlock(
                    nextBlockNumber, e.getStatus().getCode().toString(),
                    e.getStatus().getDescription()
                )
            );
            try {
                Thread.sleep(FETCH_RETRY_DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(
                ErrorMessage.invalidProtobufPayloadWhileFetchingBlock(e.getMessage()),
                e
            );
        }
    }

    /**
     * Processes a single transaction from a block, choosing the correct
     * strategy based on whether this node initiated the transaction.
     *
     * <ol>
     *   <li><b>Optimistic transfer</b> — the balance was already mutated
     *       directly. We skip re-application and record success.</li>
     *   <li><b>Canonical</b> — another node's transaction, a replay during
     *       startup, or a locally-initiated delete.</li>
     * </ol>
     *
     * @param signedTx the signed transaction to process
     * @return the canonical outcome of this transaction
     */
    private RequestOutcome processTransaction(SignedInternalTransaction signedTx) {
        InternalTransaction tx = signedTx.getInternalTransaction();
        long requestId = tx.getRequestId();

        // Verify user signature before anything else
        try {
            verifyTransactionSignature(signedTx);
        } catch (SecurityException e) {
            DebugLog.log(CLASSNAME,
                LogMessage.signatureVerificationFailedRequest(requestId, e.getMessage()));
            return RequestOutcome.failure(e);
        }

        if (state.removeFromOptimisticallyApplied(requestId)) {
            DebugLog.log(CLASSNAME,
                    LogMessage.skippingAlreadyAppliedOptimisticTransfer(requestId));
            return RequestOutcome.success();
        }

        return applyCanonical(tx);
    }

    /**
     * Normal canonical application for another node's transaction or a
     * replay during startup.
     */
    private RequestOutcome applyCanonical(InternalTransaction tx) {
        try {
            if (tx instanceof CreateWalletTransaction cwt) {
                state.createWallet(cwt.getUserId(), cwt.getWalletId());
            } else if (tx instanceof DeleteWalletTransaction dwt) {
                state.deleteWallet(dwt.getUserId(), dwt.getWalletId());
            } else if (tx instanceof TransferTransaction tt) {
                state.transfer(
                    tt.getUserId(), tt.getWalletId(),
                    tt.getDstWalletId(), tt.getValue());
            } else {
                throw new IllegalArgumentException(
                    ErrorMessage.unsupportedTransactionType(tx.getClass().getName())
                );
            }
            DebugLog.log(CLASSNAME,
                    LogMessage.appliedCanonicalTransaction(tx.getClass().getSimpleName()));
            return RequestOutcome.success();

        } catch (WalletAlreadyExistsException
                 | WalletDoesNotExistException
                 | BalanceIsNotZeroException
                 | IncorrectOwnerException
                 | SourceWalletDoesNotExistException
                 | DestinationWalletDoesNotExistException
                 | SparseBalanceFromOriginException
                 | NonPositiveTransferAmountException e) {
            DebugLog.log(CLASSNAME,
                LogMessage.failedCanonicalRequest(
                    tx.getRequestId(),
                    tx.getClass().getSimpleName(),
                    e.getMessage()
                )
            );
            return RequestOutcome.failure(e);
        }
    }

    /**
     * Verifies the RSA/SHA-256 signature attached to a {@link BlockResponse}.
     *
     * <p>The signature covers the raw protobuf bytes of the enclosed {@link Block}.
     * Returns {@code false} — rather than propagating exceptions — so that a
     * verification failure is treated as a bad block and silently dropped by the
     * caller, rather than crashing the fetch loop.
     *
     * @param blockResponse the response whose signature is to be verified
     * @return {@code true} if the signature is valid; {@code false} if the
     *         signature is invalid or if a cryptographic error prevents verification
     */
    private boolean verifySignature(BlockResponse blockResponse) {
        try {
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            Block blockGRPC = blockResponse.getBlock();
            byte[] signature = blockResponse.getSignature().toByteArray();
            sig.update(blockGRPC.toByteArray());
            return sig.verify(signature);

        } catch (InvalidKeyException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidVerificationPublicKey(e.getMessage()));
        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.verificationAlgorithmUnavailable(e.getMessage()));
        } catch (SignatureException e) {
            DebugLog.log(CLASSNAME, LogMessage.malformedSignatureBytes(e.getMessage()));
        }
        return false;
    }

    private void verifyTransactionSignature(SignedInternalTransaction signedTx) {
        InternalTransaction tx = signedTx.getInternalTransaction();
        String userId = tx.getUserId();

        PublicKey userPublicKey = NodeServiceImpl.getUserPublicKey(
            getClass().getClassLoader(),
            userId
        );

        byte[] requestBytes;
        if (tx instanceof CreateWalletTransaction cwt) {
            requestBytes = CreateWalletRequest.newBuilder()
                .setRequestId(cwt.getRequestId())
                .setUserId(cwt.getUserId())
                .setWalletId(cwt.getWalletId())
                .build().toByteArray();
        } else if (tx instanceof DeleteWalletTransaction dwt) {
            requestBytes = DeleteWalletRequest.newBuilder()
                .setRequestId(dwt.getRequestId())
                .setUserId(dwt.getUserId())
                .setWalletId(dwt.getWalletId())
                .build().toByteArray();
        } else if (tx instanceof TransferTransaction tt) {
            requestBytes = TransferRequest.newBuilder()
                .setRequestId(tt.getRequestId())
                .setSrcUserId(tt.getUserId())
                .setSrcWalletId(tt.getWalletId())
                .setDstWalletId(tt.getDstWalletId())
                .setValue(tt.getValue())
                .build().toByteArray();
        } else {
            throw new SecurityException(ErrorMessage.cannotVerifyUnsupportedTransactionType(tx.getClass().getName()));
        }

        if (!KeyManager.verifySignature(userPublicKey, requestBytes, signedTx.getSignature())) {
            throw new SecurityException(ErrorMessage.invalidUserSignature(userId, tx.getRequestId()));
        }
    }
}