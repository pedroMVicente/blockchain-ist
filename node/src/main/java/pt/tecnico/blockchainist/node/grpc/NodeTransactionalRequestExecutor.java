package pt.tecnico.blockchainist.node.grpc;

import java.util.List;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.SignedTransaction;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.domain.RequestOutcome;
import pt.tecnico.blockchainist.node.domain.StartRequestResult;
import pt.tecnico.blockchainist.node.domain.exceptions.BalanceIsNotZeroException;
import pt.tecnico.blockchainist.node.domain.exceptions.DestinationWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.IncorrectOwnerException;
import pt.tecnico.blockchainist.node.domain.exceptions.NonPositiveTransferAmountException;
import pt.tecnico.blockchainist.node.domain.exceptions.SourceWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.SparseBalanceFromOriginException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;

/**
 * Executes transactional node requests end to end: deduplication, local
 * pending-state application, broadcast to the sequencer (with dependency
 * metadata), and — for non-optimistic paths — waiting for the canonical
 * outcome before returning a gRPC response.
 *
 * <p>Each operation type has its own method because the optimistic /
 * non-optimistic branching and rollback logic differs between creates,
 * deletes, and transfers.
 */
public class NodeTransactionalRequestExecutor {
    private static final String CLASSNAME = NodeTransactionalRequestExecutor.class.getSimpleName();

    private final NodeState state;
    private final ClientSequencerService clientSequencer;
    private final NodeErrorMapper errorMapper;


    public NodeTransactionalRequestExecutor(
        NodeState state,
        ClientSequencerService clientSequencer,
        NodeErrorMapper errorMapper
    ) {
        this.state = state;
        this.clientSequencer = clientSequencer;
        this.errorMapper = errorMapper;
    }


    // -----------------------------------------------------------------------
    //  Create wallet
    // -----------------------------------------------------------------------

    /**
     * Creates a wallet: registers the request, broadcasts to the sequencer,
     * and blocks until BlockFetcher confirms or rejects the transaction
     * in a block.
     */
    public void executeCreateWallet(
        SignedTransaction transaction,
        StreamObserver<CreateWalletResponse> responseObserver
    ) {
        CreateWalletRequest createWalletRequest = transaction.getCreateWallet().getRequest();
        long requestId = createWalletRequest.getRequestId();
        String walletId = createWalletRequest.getWalletId().trim();

        StartRequestResult startResult = state.startRequest(requestId);
        boolean startedRequest = startResult.getKind() == StartRequestResult.Kind.STARTED;

        try {
            if (handleDuplicate(startResult, requestId, responseObserver)) return;

            clientSequencer.broadcastTransaction(transaction, List.of());
            DebugLog.log(CLASSNAME, LogMessage.createWalletBroadcastAccepted(requestId));

            RequestOutcome outcome = state.waitForRequestResult(requestId);
            if (!outcome.isSuccess()) {
                responseObserver.onError(errorMapper.mapDomainException(outcome.getError()));
                return;
            }

            responseObserver.onNext(CreateWalletResponse.newBuilder().build());
            responseObserver.onCompleted();
            DebugLog.log(CLASSNAME, LogMessage.createWalletResponseReturned(walletId));

        } catch (StatusRuntimeException e) {
            handleBroadcastError(e, startedRequest, requestId,
                () -> { },
                responseObserver, "createWallet",
                "walletId=" + walletId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                Status.CANCELLED.withDescription(ErrorMessage.InterruptedMessage).asRuntimeException()
            );

        }
    }


    // -----------------------------------------------------------------------
    //  Delete wallet
    // -----------------------------------------------------------------------

    /**
     * Deletes a wallet: registers the request, validates and marks the
     * wallet with {@code deletePending = true} locally, broadcasts to the
     * sequencer with dependency metadata, and blocks until BlockFetcher
     * confirms or rejects the transaction in a block.
     */
    public void executeDeleteWallet(
        SignedTransaction transaction,
        StreamObserver<DeleteWalletResponse> responseObserver
    ) {
        DeleteWalletRequest deleteWalletRequest = transaction.getDeleteWallet().getRequest();
        long requestId = deleteWalletRequest.getRequestId();
        String walletId = deleteWalletRequest.getWalletId().trim();
        String userId = deleteWalletRequest.getUserId().trim();

        StartRequestResult startResult = state.startRequest(requestId);
        boolean started = startResult.getKind() == StartRequestResult.Kind.STARTED;

        try {
            if (handleDuplicate(startResult, requestId, responseObserver)) return;

            List<Long> dependencies;
            try {
                dependencies = state.deleteWalletPending(requestId, walletId, userId);
            } catch (WalletDoesNotExistException
                     | BalanceIsNotZeroException
                     | IncorrectOwnerException e) {
                state.abandonRequest(requestId);
                responseObserver.onError(errorMapper.mapDomainException(e));
                return;
            }

            clientSequencer.broadcastTransaction(transaction, dependencies);
            DebugLog.log(CLASSNAME, LogMessage.deleteWalletBroadcastAccepted(requestId));

            RequestOutcome outcome = state.waitForRequestResult(requestId);
            if (!outcome.isSuccess()) {
                responseObserver.onError(errorMapper.mapDomainException(outcome.getError()));
                return;
            }

            responseObserver.onNext(DeleteWalletResponse.newBuilder().build());
            responseObserver.onCompleted();
            DebugLog.log(CLASSNAME, LogMessage.deleteWalletResponseReturned(walletId));

        } catch (StatusRuntimeException e) {
            handleBroadcastError(e, started, requestId,
                () -> state.rollbackDeleteWallet(walletId),
                responseObserver, "deleteWallet",
                "walletId=" + walletId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                Status.CANCELLED.withDescription(ErrorMessage.InterruptedMessage).asRuntimeException());
        }
    }


    // -----------------------------------------------------------------------
    //  Transfer
    // -----------------------------------------------------------------------

    /**
     * Transfers funds between two wallets.
     *
     * <p>The source wallet's balance is checked upfront. If insufficient,
     * the transfer is rejected immediately.
     *
     * <p><b>Optimistic path</b> (intra-org, sufficient balance): the balance
     * is mutated directly, the client gets an immediate success response,
     * and the broadcast happens fire-and-forget.
     *
     * <p><b>Non-optimistic path</b> (cross-org): the transaction is
     * broadcast and the thread blocks until BlockFetcher applies the
     * canonical result.
     */
    public void executeTransfer(
        SignedTransaction transaction,
        StreamObserver<TransferResponse> responseObserver
    ) {
        TransferRequest transferRequest = transaction.getTransfer().getRequest();
        long requestId = transferRequest.getRequestId();
        String srcWalletId = transferRequest.getSrcWalletId().trim();
        String srcUserId = transferRequest.getSrcUserId().trim();
        String dstWalletId = transferRequest.getDstWalletId().trim();
        long amount = transferRequest.getValue();

        StartRequestResult startResult = state.startRequest(requestId);
        boolean started = startResult.getKind() == StartRequestResult.Kind.STARTED;

        try {
            if (handleDuplicate(startResult, requestId, responseObserver)) return;

            NodeState.TransferPreparation prep;
            try {
                prep = state.prepareTransfer(
                    requestId, srcUserId, srcWalletId, dstWalletId, amount);
            } catch (SourceWalletDoesNotExistException
                     | DestinationWalletDoesNotExistException
                     | IncorrectOwnerException
                     | SparseBalanceFromOriginException
                     | NonPositiveTransferAmountException e) {
                state.abandonRequest(requestId);
                responseObserver.onError(errorMapper.mapDomainException(e));
                return;
            }

            if (prep.isOptimistic()) {
                responseObserver.onNext(TransferResponse.newBuilder().build());
                responseObserver.onCompleted();
                DebugLog.log(CLASSNAME, LogMessage.transferOptimisticResponseReturned(requestId));

                try {
                    clientSequencer.broadcastTransaction(
                        transaction, prep.getDependencies());
                } catch (StatusRuntimeException e) {
                    DebugLog.log(CLASSNAME,
                        LogMessage.transferOptimisticBroadcastFailed(requestId, e.getStatus().getCode().toString()));
                }

            } else {
                clientSequencer.broadcastTransaction(
                    transaction, prep.getDependencies());
                DebugLog.log(CLASSNAME, LogMessage.transferBroadcastAccepted(requestId));

                RequestOutcome outcome = state.waitForRequestResult(requestId);
                if (!outcome.isSuccess()) {
                    responseObserver.onError(
                        errorMapper.mapDomainException(outcome.getError()));
                    return;
                }

                responseObserver.onNext(TransferResponse.newBuilder().build());
                responseObserver.onCompleted();
                DebugLog.log(CLASSNAME, LogMessage.transferResponseReturned(srcWalletId));
            }

        } catch (StatusRuntimeException e) {
            handleBroadcastError(e, started, requestId,
                () -> { },
                responseObserver, "transfer",
                "srcWalletId=" + srcWalletId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                Status.CANCELLED.withDescription(ErrorMessage.InterruptedMessage).asRuntimeException());
        }
    }


    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    /**
     * Handles duplicate-request scenarios (already completed or still pending).
     * Returns {@code true} if the response was sent and the caller should return.
     */
    private <Resp> boolean handleDuplicate(
        StartRequestResult startResult,
        long requestId,
        StreamObserver<Resp> responseObserver
    ) throws InterruptedException {
        if (startResult.getKind() == StartRequestResult.Kind.DUPLICATE_COMPLETED) {
            responseObserver.onError(
                buildDuplicateRequestError(requestId, startResult.getCompletedOutcome()));
            return true;
        }
        if (startResult.getKind() == StartRequestResult.Kind.DUPLICATE_PENDING) {
            RequestOutcome outcome = state.waitForRequestResult(requestId);
            responseObserver.onError(
                buildDuplicateRequestError(requestId, outcome));
            return true;
        }
        return false;
    }

    /**
     * Handles a {@link StatusRuntimeException} thrown during broadcast.
     *
     * <p>If the sequencer reports ABORTED (duplicate requestId), the
     * transaction is already sequenced — we wait for the block result
     * instead of rolling back.  For any other error, the local pending
     * state is rolled back and the error is forwarded to the client.
     */
    private <Resp> void handleBroadcastError(
        StatusRuntimeException e,
        boolean started,
        long requestId,
        Runnable rollback,
        StreamObserver<Resp> responseObserver,
        String operation,
        String details
    ) {
        if (started && e.getStatus().getCode() == Status.Code.ABORTED) {
            DebugLog.log(CLASSNAME, LogMessage.duplicateRejectedBySequencer(operation, requestId));
            try {
                RequestOutcome outcome = state.waitForRequestResult(requestId);
                responseObserver.onError(
                    buildDuplicateRequestError(requestId, outcome));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                responseObserver.onError(
                    Status.CANCELLED.withDescription(ErrorMessage.InterruptedMessage).asRuntimeException());
            }
            return;
        }

        rollback.run();
        if (started) state.abandonRequest(requestId);
        logGrpcError(operation, "broadcast", e,
            details + " requestId=" + requestId);
        responseObserver.onError(e);
    }

    /**
     * Logs a gRPC failure that occurred while processing a transactional request.
     */
    private void logGrpcError(
        String operation,
        String phase,
        StatusRuntimeException exception,
        String details
    ) {
        DebugLog.log(CLASSNAME,
            LogMessage.grpcError(
                operation,
                phase,
                exception.getStatus().getCode().toString(),
                exception.getStatus().getDescription(),
                details));
    }


    /**
     * Builds the duplicate-request error returned when a request identifier is reused.
     *
     * @param requestId the repeated request identifier
     * @param outcome the canonical outcome of the original request
     * @return the gRPC duplicate-request error
     */
    private StatusRuntimeException buildDuplicateRequestError(long requestId, RequestOutcome outcome) {
        return Status.ABORTED
            .withDescription(
                ErrorMessage.duplicatedTransactionRequest(requestId, outcome.duplicateSummary())
            )
            .asRuntimeException();
    }
}
