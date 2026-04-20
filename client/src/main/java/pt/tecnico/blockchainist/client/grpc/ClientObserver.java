package pt.tecnico.blockchainist.client.grpc;

import java.util.concurrent.CompletableFuture;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.client.domain.message.LogMessage;

/**
 * Generic gRPC stream observer that bridges async RPC callbacks to a completion future.
 *
 * @param <R> the RPC response message type
 */
public class ClientObserver<R> implements StreamObserver<R> {
    private static final String CLASSNAME = ClientObserver.class.getSimpleName();
    private final CompletableFuture<Void> future;

    /**
     * Creates a new observer that completes the given future when the RPC finishes.
     *
     * @param future the completion future associated with the async RPC call
     */
    public ClientObserver(CompletableFuture<Void> future) {
        this.future = future;
    }

    /**
     * Handles a response message emitted by the RPC stream.
     *
     * @param response the received response message
     */
    @Override
    public void onNext(R response) {
        DebugLog.log(CLASSNAME, response.toString());
    }

    /**
     * Handles stream failure by logging details and completing the future exceptionally.
     *
     * @param t the error reported by the gRPC stream
     */
    @Override
    public void onError(Throwable t) {
        StatusRuntimeException e = Status.fromThrowable(t).asRuntimeException();
        DebugLog.log(CLASSNAME, LogMessage.rpcError(
            "async",
            e.getStatus().getCode().toString(),
            e.getStatus().getDescription())
        );
        future.completeExceptionally(t);
    }

    /**
     * Handles normal stream completion and marks the future as successful.
     */
    @Override
    public void onCompleted() {
        DebugLog.log(CLASSNAME, LogMessage.REQUEST_COMPLETED);
        future.complete(null);
    }
}