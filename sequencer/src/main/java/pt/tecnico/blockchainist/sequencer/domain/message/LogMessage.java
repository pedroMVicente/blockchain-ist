package pt.tecnico.blockchainist.sequencer.domain.message;

/**
 * Centralized log messages used throughout the sequencer domain and gRPC layer.
 */
public interface LogMessage {

    /** Message used when a transaction is accepted by the sequencer. */
    static String transactionAccepted(long requestId) {
        return "Transaction accepted requestId=" + requestId;
    }

    /** Message used when a broadcast response is returned successfully. */
    String BroadcastResponseReturnedMessage = "broadcast response returned";

    /** Message used when a broadcast request fails because of an unexpected internal error. */
    String BroadcastInternalServerErrorMessage = "broadcast failed: internal server error";

    /** Message used when a broadcast payload cannot be parsed after decryption. */
    String BroadcastInvalidProtobufPayloadMessage = "broadcast failed: invalid protobuf payload";

    /** Message used when a deliverBlock request fails because of an unexpected internal error. */
    String DeliverBlockInternalServerErrorMessage = "deliverBlock failed: internal server error";

    /** Message used when a deliverBlock payload cannot be parsed after decryption. */
    String DeliverBlockInvalidProtobufPayloadMessage = "deliverBlock failed: invalid protobuf payload";

    /** Message used when a deliverBlock wait is cancelled due to interruption. */
    String OperationCancelledMessage = "Operation cancelled";

    /** Message used when block signing fails inside the sequencer. */
    String BlockSigningFailedMessage = "Block signing failed";

    /** Message used when broadcast is rejected because the sequencer is unavailable. */
    static String broadcastRejectedSequencerUnavailable(String reason) {
        return "broadcast rejected: sequencer unavailable: " + reason;
    }

    /** Message used when broadcast fails because the transaction is invalid. */
    static String broadcastFailedBadTransaction(String reason) {
        return "broadcast failed: bad transaction: " + reason;
    }

    /** Message used when broadcast fails unexpectedly with a runtime exception. */
    static String broadcastFailedUnexpectedly(String exceptionType, String reason) {
        return "broadcast failed unexpectedly: " + exceptionType +
            " message=" + reason;
    }

    /** Message used when broadcast payload parsing fails after decryption. */
    static String broadcastInvalidProtobufPayload(String reason) {
        return "broadcast failed: invalid protobuf payload after decryption: " + reason;
    }

    /** Message used when a deliverBlock request is received. */
    static String deliverBlockReceivedRequest(int blockNumber) {
        return "deliverBlock received request blockNumber=" + blockNumber;
    }

    /** Message used when a deliverBlock response is returned successfully. */
    static String deliverBlockResponseReturned(int blockNumber) {
        return "deliverBlock response returned blockNumber=" + blockNumber;
    }

    /** Message used when deliverBlock is aborted because the sequencer is unavailable. */
    static String deliverBlockAbortedSequencerUnavailable(String reason) {
        return "deliverBlock aborted: sequencer unavailable: " + reason;
    }

    /** Message used when deliverBlock waiting is interrupted. */
    static String deliverBlockInterruptedWhileWaiting(String reason) {
        return "deliverBlock interrupted while waiting for block to be sealed: " + reason;
    }

    /** Message used when deliverBlock fails unexpectedly with a runtime exception. */
    static String deliverBlockFailedUnexpectedly(String exceptionType, String reason) {
        return "deliverBlock failed unexpectedly: " + exceptionType +
            " message=" + reason;
    }

    /** Message used when deliverBlock payload parsing fails after decryption. */
    static String deliverBlockInvalidProtobufPayload(String reason) {
        return "deliverBlock failed: invalid protobuf payload after decryption: " + reason;
    }

    /** Message used when block signing fails because the algorithm is unavailable. */
    static String blockSigningAlgorithmUnavailable(String reason) {
        return "Block signing failed: SHA256withRSA algorithm unavailable: " + reason;
    }

    /** Message used when block signing fails because the private key is invalid. */
    static String blockSigningInvalidKey(String reason) {
        return "Block signing failed: private key is invalid or incompatible: " + reason;
    }

    /** Message used when block signing fails because the signature engine is in an invalid state. */
    static String blockSigningInvalidState(String reason) {
        return "Block signing failed: signature engine is in an invalid state: " + reason;
    }

    /** Message used when the RSA KeyFactory algorithm is unavailable while loading the sequencer public key. */
    static String rsaKeyFactoryUnavailable(String reason) {
        return "RSA KeyFactory algorithm is unavailable on this JVM: " + reason;
    }

    /** Message used when the sequencer private key file is malformed or invalid. */
    static String invalidOrMalformedSequencerPrivateKey(String reason) {
        return "privateSequencer.der contains an invalid or malformed RSA private key: " + reason;
    }

    /** Message used when the sequencer private key file cannot be read from the classpath. */
    static String failedToReadSequencerPrivateKey(String reason) {
        return "Failed to read privateSequencer.der from classpath: " + reason;
    }

    /** Message used when the sequencer AES key cannot be loaded at startup. */
    static String FailedToLoadSequencerAESKeyMessage(String path) {
        return "Failed to load sequencer AES key — check that '" + path + "' exists on the classpath";
    }

    /** Message used when a duplicated transaction requestId is sent to the sequencer. */
    static String duplicatedTransactionRequestId(long requestId) {
        return "Duplicated transaction requestId: " + requestId;
    }

    /** Message used when a broadcast request fails because the transaction is invalid. */
    static String broadcastFailed(String reason) {
        return "broadcast failed: " + reason;
    }

    /** Message used when logging the configured maximum number of transactions for a new block. */
    static String blockSize(int blockNumber, int maxTransactions) {
        return blockNumber + "# Block size: " + maxTransactions;
    }

    /** Message used when logging the configured timeout for a new block. */
    static String blockTimeout(int blockNumber, long maxSeconds) {
        return blockNumber + "# Block timeout: " + maxSeconds;
    }
}
