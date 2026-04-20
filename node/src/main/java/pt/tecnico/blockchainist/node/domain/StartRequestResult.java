package pt.tecnico.blockchainist.node.domain;

/**
 * Result of trying to register a request in the node request tracker.
 */
public final class StartRequestResult {
    public enum Kind {
        STARTED,
        DUPLICATE_COMPLETED,
        DUPLICATE_PENDING
    }

    private final Kind kind;
    private final RequestOutcome completedOutcome;

    /**
     * Creates a new start-request classification.
     *
     * @param kind the classification of the request
     * @param completedOutcome the canonical outcome when the request was already completed
     */
    private StartRequestResult(Kind kind, RequestOutcome completedOutcome) {
        this.kind = kind;
        this.completedOutcome = completedOutcome;
    }


    /**
     * Creates a result indicating that the request was newly started.
     *
     * @return the started classification
     */
    public static StartRequestResult started() {
        return new StartRequestResult(Kind.STARTED, null);
    }


    /**
     * Creates a result indicating that the request was already completed.
     *
     * @param outcome the canonical outcome of the completed request
     * @return the duplicate-completed classification
     */
    public static StartRequestResult duplicateCompleted(RequestOutcome outcome) {
        return new StartRequestResult(Kind.DUPLICATE_COMPLETED, outcome);
    }


    /**
     * Creates a result indicating that the request is already pending.
     *
     * @return the duplicate-pending classification
     */
    public static StartRequestResult duplicatePending() {
        return new StartRequestResult(Kind.DUPLICATE_PENDING, null);
    }


    /**
     * Returns the classification of the request start attempt.
     *
     * @return the request classification
     */
    public Kind getKind() {
        return kind;
    }


    /**
     * Returns the canonical outcome when the request was already completed.
     *
     * @return the stored canonical outcome, or {@code null} otherwise
     */
    public RequestOutcome getCompletedOutcome() {
        return completedOutcome;
    }
}