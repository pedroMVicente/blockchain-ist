package pt.tecnico.blockchainist.client.domain.message;

public interface ErrorMessage {

    // -------------------------------------------------------------------------
    // Generic / shared argument errors
    // -------------------------------------------------------------------------

    /** E.g. "Expected 5 arguments, got 3" */
    static String wrongArgCount(int expected, int actual) {
        return "Expected " + expected + " arguments, got " + actual;
    }

    /** E.g. "Node index must be between 0 and 3" */
    static String nodeIndexOutOfRange(int maxIndex) {
        return "Node index must be between 0 and " + maxIndex;
    }

    String NODE_DELAY_NEGATIVE =
            "Node delay cannot be negative";

    // -------------------------------------------------------------------------
    // ID validation errors
    // -------------------------------------------------------------------------

    /** E.g. "Expected User ID to be composed of ASCII alphanumeric characters, got \"alice#\"" */
    static String invalidUserId(String value) {
        return "Expected User ID to be composed of ASCII alphanumeric characters, got \"" + value + "\"";
    }

    /** E.g. "Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"w@1\"" */
    static String invalidWalletId(String value) {
        return "Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + value + "\"";
    }

    /** E.g. "Expected Source User ID to be composed of ASCII alphanumeric characters, got \"bad!\"" */
    static String invalidSourceUserId(String value) {
        return "Expected Source User ID to be composed of ASCII alphanumeric characters, got \"" + value + "\"";
    }

    /** E.g. "Expected Source Wallet ID to be composed of ASCII alphanumeric characters, got \"bad!\"" */
    static String invalidSourceWalletId(String value) {
        return "Expected Source Wallet ID to be composed of ASCII alphanumeric characters, got \"" + value + "\"";
    }

    /** E.g. "Expected Destination Wallet ID to be composed of ASCII alphanumeric characters, got \"bad!\"" */
    static String invalidDestinationWalletId(String value) {
        return "Expected Destination Wallet ID to be composed of ASCII alphanumeric characters, got \"" + value + "\"";
    }

    // -------------------------------------------------------------------------
    // Numeric argument errors  (per-command)
    // -------------------------------------------------------------------------

    String CREATE_NUMERIC_ARGS_INVALID =
            "Expected initial balance, node number, and node delay to be integers";

    String DELETE_NUMERIC_ARGS_INVALID =
            "Expected node number and node delay to be integers";

    String BALANCE_NUMERIC_ARGS_INVALID =
            "Expected node number and node delay to be integers";

    String TRANSFER_NUMERIC_ARGS_INVALID =
            "Expected amount, node number, and node delay to be integers";

    String DEBUG_NUMERIC_ARGS_INVALID =
            "Expected node index to be an integer";

    String PAUSE_NUMERIC_ARGS_INVALID =
            "Expected pause time to be an integer";

    // -------------------------------------------------------------------------
    // Domain / business-rule errors
    // -------------------------------------------------------------------------

    String AMOUNT_NEGATIVE =
            "Amount cannot be negative";

    String PAUSE_TIME_NEGATIVE =
            "Pause time cannot be negative";

    // -------------------------------------------------------------------------
    // Node connectivity / retry errors
    // -------------------------------------------------------------------------

    /** E.g. "ERROR 3: Node is unavailable" */
    static String nodeUnavailable(long commandNumber) {
        return "ERROR " + commandNumber + ": Node is unavailable";
    }

    /** E.g. "ERROR 3: <detail>" */
    static String nodeError(long commandNumber, String detail) {
        return "ERROR " + commandNumber + ": " + detail;
    }

    String ALL_NODES_FAILED = "All nodes failed to respond in time";
}