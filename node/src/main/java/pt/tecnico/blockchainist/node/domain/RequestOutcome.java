package pt.tecnico.blockchainist.node.domain;

import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;

/**
 * Immutable representation of the canonical result of a request.
 * A request either completed successfully or completed with an exception.
 */
public final class RequestOutcome {
    private static final RequestOutcome SUCCESS = new RequestOutcome(null);

    private final Exception error;

    /**
     * Creates a new outcome instance.
     *
     * @param error the canonical error, or {@code null} for success
     */
    private RequestOutcome(Exception error) {
        this.error = error;
    }


    /**
     * Returns the shared success outcome.
     *
     * @return the canonical success outcome
     */
    public static RequestOutcome success() {
        return SUCCESS;
    }


    /**
     * Creates a canonical failure outcome.
     *
     * @param error the failure that represents the canonical outcome
     * @return the failure outcome
     */
    public static RequestOutcome failure(Exception error) {
        if (error == null) {
            throw new IllegalArgumentException(ErrorMessage.NullErrorMessage);
        }
        return new RequestOutcome(error);
    }


    /**
     * Indicates whether the canonical outcome is success.
     *
     * @return {@code true} when the request succeeded
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Returns the canonical error when the request failed.
     *
     * @return the stored failure, or {@code null} when the canonical outcome is success
     */
    public Exception getError() {
        return error;
    }

    /**
     * Builds the text used when reporting a duplicated request.
     *
     * @return a human-readable summary of the original canonical outcome
     */
    public String duplicateSummary() {
        if (isSuccess()) {
            return "Original result: OK";
        }
        return "Original error: " + error.getMessage();
    }
}