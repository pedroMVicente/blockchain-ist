package pt.tecnico.blockchainist.client.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

import io.grpc.Status;
import static io.grpc.Status.Code.ABORTED;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.UNAVAILABLE;

import pt.tecnico.blockchainist.client.domain.exceptions.ClientNodeServiceException;
import pt.tecnico.blockchainist.client.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.common.transaction.InternalTransaction;

/**
 * Dispatches wallet operations to nodes, handling requestId regeneration on ABORTED
 * (duplicate-ID collision) for both blocking and async call paths.
 */
public class NodeOperationDispatcher {

    private final ArrayList<ClientNodeService> nodes;

    /**
     * Creates a dispatcher backed by the given list of nodes.
     *
     * @param nodes the node stubs the client may send operations to
     */
    public NodeOperationDispatcher(ArrayList<ClientNodeService> nodes) {
        this.nodes = nodes;
    }

    // -------------------------------------------------------------------------
    // Blocking dispatch
    // -------------------------------------------------------------------------

    /**
     * Asks a node to create a wallet owned by the given user, blocking until it completes.
     * Retries on other nodes if the chosen node times out.
     *
     * @param userId    the ID of the user who will own the wallet
     * @param walletId  the ID of the wallet to create
     * @param nodeIndex the preferred node to send the request to
     * @param nodeDelay the artificial delay in seconds to request from the node
     */
    public void createWalletBlocking(String userId, String walletId, int nodeIndex, int nodeDelay) {
        withBlockingRetry(nodeIndex,
            (requestId, i) -> nodes.get(i).createWallet(requestId, userId, walletId, nodeDelay, true));
    }

    /**
     * Asks a node to delete a wallet owned by the given user, blocking until it completes.
     * Retries on other nodes if the chosen node times out.
     *
     * @param userId    the ID of the user who owns the wallet
     * @param walletId  the ID of the wallet to delete
     * @param nodeIndex the preferred node to send the request to
     * @param nodeDelay the artificial delay in seconds to request from the node
     */
    public void deleteWalletBlocking(String userId, String walletId, int nodeIndex, int nodeDelay) {
        withBlockingRetry(nodeIndex,
            (requestId, i) -> nodes.get(i).deleteWallet(requestId, userId, walletId, nodeDelay, true));
    }

    /**
     * Asks a node to transfer funds between two wallets, blocking until it completes.
     * Retries on other nodes if the chosen node times out.
     *
     * @param sourceUserId          the ID of the user authorising the transfer
     * @param sourceWalletId        the wallet to debit
     * @param destinationWalletId   the wallet to credit
     * @param amount                the amount to transfer
     * @param nodeIndex             the preferred node to send the request to
     * @param nodeDelay             the artificial delay in seconds to request from the node
     */
    public void transferBlocking(String sourceUserId, String sourceWalletId,
            String destinationWalletId, long amount, int nodeIndex, int nodeDelay) {
        withBlockingRetry(nodeIndex,
            (requestId, i) -> nodes.get(i).transfer(requestId, sourceUserId, sourceWalletId,
                destinationWalletId, amount, nodeDelay, true));
    }

    /**
     * Reads the balance of a wallet from a node, blocking until it completes.
     * Retries on other nodes if the chosen node times out.
     *
     * @param walletId  the ID of the wallet to query
     * @param nodeIndex the preferred node to send the request to
     * @param nodeDelay the artificial delay in seconds to request from the node
     * @return the current balance of the wallet
     */
    public long readBalance(String walletId, int nodeIndex, int nodeDelay) {
        return withBlockingRetryReturning(nodeIndex,
            i -> nodes.get(i).readBalance(walletId, nodeDelay));
    }

    /**
     * Retrieves the full list of committed transactions from a node, blocking until it completes.
     * Retries on other nodes if the chosen node times out.
     *
     * @param nodeIndex the preferred node to send the request to
     * @return the ordered list of committed transactions
     */
    public List<InternalTransaction> getBlockchainState(int nodeIndex) {
        return withBlockingRetryReturning(nodeIndex,
            i -> nodes.get(i).getBlockchainState());
    }

    /**
     * Sends a blocking transactional RPC to the preferred node, retrying on subsequent nodes
     * on timeout. On ABORTED at the first node, regenerates the ID and retries the same node
     * once — this handles the rare case of a requestId collision. On ABORTED at any later
     * node, the transaction was already processed by a previous node that timed out
     * before it could reply, so we return whatever outcome that node recorded.
     * 
     * Throws {@link ClientNodeServiceException} with ALL_NODES_FAILED if all nodes time out.
     *
     * @param startIndex the preferred node index to start from
     * @param call       the operation to execute, receiving a requestId and a node index
     */
    private void withBlockingRetry(int startIndex, BlockingCall call) {
        long requestId = newRequestId();
        for (int attempt = 0; attempt < nodes.size(); attempt++) {
            int i = (startIndex + attempt) % nodes.size();
            try {
                call.execute(requestId, i);
                return;
            } catch (ClientNodeServiceException e) {
                Status.Code code = e.getStatusCode();
                if (code == ABORTED) {
                    if (attempt == 0) {
                        requestId = newRequestId();
                    } else {
                        return;
                    }
                } else if (code != DEADLINE_EXCEEDED && code != UNAVAILABLE) {
                    throw e;
                }
            }
        }
        throw new ClientNodeServiceException(ErrorMessage.ALL_NODES_FAILED, DEADLINE_EXCEEDED);
    }

    /**
     * Sends a blocking read-only RPC to the preferred node, retrying on subsequent nodes
     * on timeout, and returns the result of the first successful call.
     * Throws {@link ClientNodeServiceException} with ALL_NODES_FAILED if all nodes time out.
     *
     * @param startIndex the preferred node index to start from
     * @param call       the operation to execute, receiving a node index and returning a result
     * @return the result of the first successful call
     */
    private <T> T withBlockingRetryReturning(int startIndex, IntFunction<T> call) {
        for (int attempt = 0; attempt < nodes.size(); attempt++) {
            int i = (startIndex + attempt) % nodes.size();
            try {
                return call.apply(i);
            } catch (ClientNodeServiceException e) {
                Status.Code code = e.getStatusCode();
                if (code != DEADLINE_EXCEEDED && code != UNAVAILABLE) throw e;
            }
        }
        throw new ClientNodeServiceException(ErrorMessage.ALL_NODES_FAILED, DEADLINE_EXCEEDED);
    }

    @FunctionalInterface
    private interface BlockingCall {
        void execute(long requestId, int nodeIndex);
    }

    // -------------------------------------------------------------------------
    // Async dispatch
    // -------------------------------------------------------------------------

    /**
     * Asks a node to create a wallet owned by the given user, returning immediately with a future
     * that completes when the operation finishes. Retries on other nodes if the chosen node times out.
     *
     * @param userId    the ID of the user who will own the wallet
     * @param walletId  the ID of the wallet to create
     * @param nodeIndex the preferred node to send the request to
     * @param nodeDelay the artificial delay in seconds to request from the node
     * @return a future that completes successfully when the wallet is created, or fails with
     *         a {@link ClientNodeServiceException} if all nodes time out or the operation is rejected
     */
    public CompletableFuture<Void> createWalletAsync(
            String userId, String walletId, int nodeIndex, int nodeDelay) {
        return withAsyncRetry(nodeIndex, 0, newRequestId(),
            (requestId, i) -> nodes.get(i).createWallet(requestId, userId, walletId, nodeDelay, false));
    }

    /**
     * Asks a node to delete a wallet owned by the given user, returning immediately with a future
     * that completes when the operation finishes. Retries on other nodes if the chosen node times out.
     *
     * @param userId    the ID of the user who owns the wallet
     * @param walletId  the ID of the wallet to delete
     * @param nodeIndex the preferred node to send the request to
     * @param nodeDelay the artificial delay in seconds to request from the node
     * @return a future that completes successfully when the wallet is deleted, or fails with
     *         a {@link ClientNodeServiceException} if all nodes time out or the operation is rejected
     */
    public CompletableFuture<Void> deleteWalletAsync(
            String userId, String walletId, int nodeIndex, int nodeDelay) {
        return withAsyncRetry(nodeIndex, 0, newRequestId(),
            (requestId, i) -> nodes.get(i).deleteWallet(requestId, userId, walletId, nodeDelay, false));
    }

    /**
     * Asks a node to transfer funds between two wallets, returning immediately with a future
     * that completes when the operation finishes. Retries on other nodes if the chosen node times out.
     *
     * @param sourceUserId          the ID of the user authorising the transfer
     * @param sourceWalletId        the wallet to debit
     * @param destinationWalletId   the wallet to credit
     * @param amount                the amount to transfer
     * @param nodeIndex             the preferred node to send the request to
     * @param nodeDelay             the artificial delay in seconds to request from the node
     * @return a future that completes successfully when the transfer is committed, or fails with
     *         a {@link ClientNodeServiceException} if all nodes time out or the operation is rejected
     */
    public CompletableFuture<Void> transferAsync(
            String sourceUserId, String sourceWalletId, String destinationWalletId,
            long amount, int nodeIndex, int nodeDelay) {
        return withAsyncRetry(nodeIndex, 0, newRequestId(),
            (requestId, i) -> nodes.get(i).transfer(requestId, sourceUserId, sourceWalletId,
                destinationWalletId, amount, nodeDelay, false));
    }

    /**
     * Sends an async transactional RPC, retrying on subsequent nodes on timeout. Follows the same
     * ABORTED handling as the blocking variant — on the first attempt it means a requestId collision
     * so we regenerate the ID and retry; on any later attempt, the transaction was already processed
     * by a previous node that timed out before it could reply, so we return that recorded outcome.
     * Returns a failed future with ALL_NODES_FAILED if all nodes time out.
     *
     * @param startIndex the preferred node index to start from
     * @param attempt    the current attempt number
     * @param requestId  the requestId to use for this attempt
     * @param call       the operation to execute, receiving a requestId and a node index
     * @return a future that completes when the operation succeeds or fails definitively
     */
    private CompletableFuture<Void> withAsyncRetry(
            int startIndex, int attempt, long requestId, AsyncCall call) {
        if (attempt >= nodes.size()) {
            return CompletableFuture.failedFuture(
                new ClientNodeServiceException(ErrorMessage.ALL_NODES_FAILED, DEADLINE_EXCEEDED));
        }
        int i = (startIndex + attempt) % nodes.size();
        return call.execute(requestId, i).exceptionallyCompose(ex -> {
            Throwable cause = AsyncUtils.unwrapAsyncThrowable(ex);
            Status status = Status.fromThrowable(cause);
            Status.Code code = status.getCode();
            if (code == DEADLINE_EXCEEDED || code == UNAVAILABLE) {
                return withAsyncRetry(startIndex, attempt + 1, requestId, call);
            }
            if (code == ABORTED) {
                if (attempt == 0) {
                    return withAsyncRetry(startIndex, 0, newRequestId(), call);
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            }
            return CompletableFuture.failedFuture(
                new ClientNodeServiceException(status.getDescription(), code));
        });
    }

    @FunctionalInterface
    private interface AsyncCall {
        CompletableFuture<Void> execute(long requestId, int nodeIndex);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Generates a random positive requestId. The space is large enough that collisions
     * between concurrent requests are astronomically unlikely.
     *
     * @return a random requestId in the range [1, {@link Long#MAX_VALUE})
     */
    private static long newRequestId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}