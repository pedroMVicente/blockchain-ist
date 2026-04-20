package pt.tecnico.blockchainist.node.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;

public class NodeInterceptor implements ServerInterceptor {

    private static final String CLASSNAME = NodeInterceptor.class.getSimpleName();
    private static final int MAX_DELAY_SECONDS = 60;

    static final Metadata.Key<String> CLIENT_DELAY_KEY =
            Metadata.Key.of("delay_seconds", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        Listener<ReqT> delegate = next.startCall(call, headers);

        return new ServerCall.Listener<>() {
            @Override
            public void onMessage(ReqT message) {
                applyDelay(headers);
                delegate.onMessage(message);
            }
            @Override public void onHalfClose()  { delegate.onHalfClose(); }
            @Override public void onCancel()     { delegate.onCancel(); }
            @Override public void onComplete()   { delegate.onComplete(); }
            @Override public void onReady()      { delegate.onReady(); }
        };
    }

    private void applyDelay(Metadata headers) {
        String delayValue = headers.get(CLIENT_DELAY_KEY);
        if (delayValue == null) return;

        try {
            int delaySeconds = Math.min(Math.max(0, Integer.parseInt(delayValue)), MAX_DELAY_SECONDS);
            DebugLog.log(CLASSNAME, LogMessage.delayingCallBySeconds(delaySeconds));
            Thread.sleep(delaySeconds * 1000L);
        } catch (NumberFormatException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidDelayValueIgnored(delayValue));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DebugLog.log(CLASSNAME, LogMessage.DelayInterruptedMessage);
        }
    }
}