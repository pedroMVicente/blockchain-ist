package pt.tecnico.blockchainist.client.grpc;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Utility helpers for asynchronous error handling.
 */
public class AsyncUtils {
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AsyncUtils() {}

    /**
     * Unwraps wrapper exceptions commonly used by async APIs to expose the root cause.
     *
     * @param error the throwable returned by async completion callbacks
     * @return the innermost non-wrapper cause
     */
    public static Throwable unwrapAsyncThrowable(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
            || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}