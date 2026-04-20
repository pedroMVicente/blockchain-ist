package pt.tecnico.blockchainist.sequencer.domain.message;

/**
 * Centralized error messages used by the sequencer domain and gRPC service.
 */
public interface ErrorMessage {

    /** Message used when a block violates the sequencer's expected validity rules. */
    String InvalidBlockMessage = "The block is invalid. Find another block.";

    /** Message used when an operation is attempted after shutdown has started. */
    String SequencerShutdownMessage = "Sequencer is shutting down";

    /** Message used when the sequencer RSA private key cannot be loaded at startup. */
    String FailedToLoadSequencerPrivateKeyMessage = "Failed to load sequencer RSA private key";

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

    /** Message used when a transaction is appended to a block that is already ready to close. */
    String CannotAddTransactionToClosedBlockMessage = "Cannot add transaction to a closed block";

    /** Message used when a duplicated transaction requestId is sent to the sequencer. */
    static String duplicatedTransactionRequestId(long requestId) {
        return "Duplicated transaction requestId: " + requestId;
    }

    /** Message used when a broadcast request fails because the transaction is invalid. */
    static String broadcastFailed(String reason) {
        return "broadcast failed: " + reason;
    }

    /** Message used when the sequencer AES key cannot be loaded at startup. */
    static String FailedToLoadSequencerAESKeyMessage(String path){
        return "Failed to load sequencer AES key — check that '" + path + "' exists on the classpath";
    }
}
