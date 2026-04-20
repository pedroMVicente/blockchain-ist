package pt.tecnico.blockchainist.node.domain.message;

/**
 * Interface containing constant error messages used throughout the node.
 */
public interface ErrorMessage {

    /** Message used when the provided owner ID does not match the wallet's registered owner. */
    String IncorrectOwnerIdMessage = "The owner of the wallet doesn't match the wallet that was introduced";

    /** Message used when a wallet lookup fails because the wallet does not exist in the system. */
    String WalletDoesNotExistMessage = "The wallet does not exist";

    /** Message used when attempting to create a wallet that already exists in the system. */
    String WalletAlreadyExistsMessage = "The wallet already exists";

    /** Message used when attempting to delete or operate on a wallet whose balance is not zero. */
    String BalanceIsNotZeroMessage = "The balance of the wallet is not zero";

    /** Message used when the destination wallet for a transfer does not exist. */
    String DestinationWalletDoesNotExistMessage = "The destination wallet does not exist";

    /** Message used when the source wallet for a transfer does not exist. */
    String SourceWalletDoesNotExistMessage = "The source wallet does not exist";

    /** Message used when a transfer amount is zero or negative. */
    String NonPositiveTransferAmountMessage = "The transfer amount is not positive";

    /** Message used when the source wallet does not have sufficient balance for the transfer. */
    String SparseBalanceFromOriginMessage = "Not enough balance for the transaction";

    /** Message used when the initial state could not be created successfully. */
    String InitialStateCouldNotBeCreatedMessage = "The initial state could not be created";

    /** Message used when a user ID fails input validation (non-empty ASCII alphanumeric required). */
    String InvalidUserIdMessage = "Invalid userId: must be non-empty ASCII alphanumeric";

    /** Message used when a user ID is valid but does not belong to the node's organization. */
    String UserDoesNotBelongToOrganizationMessage = "The user does not belong to the node's organization";

    /** Message used when a wallet ID fails input validation (non-empty ASCII alphanumeric required). */
    String InvalidWalletIdMessage = "Invalid walletId: must be non-empty ASCII alphanumeric";

    /** Message used when a source user ID fails input validation (non-empty ASCII alphanumeric required). */
    String InvalidSourceUserIdMessage = "Invalid srcUserId: must be non-empty ASCII alphanumeric";

    /** Message used when a source wallet ID fails input validation (non-empty ASCII alphanumeric required). */
    String InvalidSourceWalletIdMessage = "Invalid srcWalletId: must be non-empty ASCII alphanumeric";

    /** Message used when a destination wallet ID fails input validation (non-empty ASCII alphanumeric required). */
    String InvalidDestinationWalletIdMessage = "Invalid dstWalletId: must be non-empty ASCII alphanumeric";

    /** Message used when a transfer amount fails validation (must be strictly positive). */
    String InvalidTransferAmountMessage = "Invalid value: transfer amount must be strictly positive";

    /** Prefix for broadcast phase errors from sequencer communication. */
    String BroadcastPhasePrefix = "broadcast phase: ";

    /** Prefix for deliver phase errors from sequencer communication. */
    String DeliverPhasePrefix = "deliver phase: ";

    /** Message used when the sequencer returns a transaction type that is not recognized by the node. */
    String UnexpectedTransactionType = "Sequencer returned unexpected transaction type";

    /** Message used when the sequencer returns a transaction with an invalid signature that cannot be verified. */
    String UnauthorisedUserSignatureException = "Unauthorised signature received";

    /** Message used when the sequencer cannot find the authorized_clients.json file in the resources. */
    String CouldNotFindAuthorizedClientsJsonMessage = "Could not find authorized_clients.json in resources";

    /** Message used when the authorized_clients.json file is not a JSON array as expected. */
    String InvalidAuthorizedClientsJsonFormatMessage = "Invalid authorized_clients.json format: expected a JSON array";

    /** Message used when the authorized_clients.json file contains an array element that is not a JSON object. */
    String InvalidAuthorizedClientsJsonElementFormatMessage = "Invalid authorized_clients.json format: each array element must be an object";

    /** Message used when the authorized_clients.json file contains an object that does not have valid user_id and organization string fields. */
    String InvalidAuthorizedClientsJsonObjectFormatMessage = "Invalid authorized_clients.json format: each object must contain string fields user_id and organization";

    /** Message used when a null error is passed to a method that expects a non-null error. */
    String NullErrorMessage = "Error cannot be null";

    /** Message used when waiting for node initialization is interrupted before a read can proceed. */
    String InterruptedWhileWaitingForNodeInitializationMessage =
        "Interrupted while waiting for node initialization";

    /** Message used when an unexpected error occurs during transaction processing.
     * This message is intentionally generic to avoid exposing internal details. */
    String InternalErrorException = "Error while processing the transaction";

        /** Message used when a transactional request is interrupted before completion. */
    String InterruptedMessage = "Interrupted";

    /** Message used when a block is recorded in a different order than expected locally. */
    static String ProcessedBlockOutOfOrder(int expectedBlockNumber, int actualBlockNumber) {
        return "Processed block out of order: expected " + expectedBlockNumber +
            " but got " + actualBlockNumber;
    }

    /** Message used when a pending request is abandoned before reaching a canonical outcome. */
    static String RequestAbandoned(long requestId) {
        return "Request abandoned: " + requestId + " was abandoned";
    }

    /** Message used when a request is unknown to both the pending and completed request sets. */
    static String RequestNeitherPendingNorCompleted(long requestId) {
        return "Request " + requestId + " is neither pending nor completed";
    }

    /** Message used when waiting for a request result fails for an unexpected internal reason. */
    static String UnexpectedErrorWhileWaitingForRequestResult(long requestId) {
        return "Unexpected error while waiting for request " + requestId + " result";
    }

    /** Message used when authorized_clients.json cannot be read from the classpath resource stream. */
    static String couldNotReadAuthorizedClientsJson(String reason) {
        return "Could not read authorized_clients.json: " + reason;
    }

    /** Message used when authorized_clients.json cannot be parsed as valid JSON. */
    static String couldNotParseAuthorizedClientsJson(String reason) {
        return "Could not parse authorized_clients.json: " + reason;
    }

    /** Message used when a fetched block payload cannot be parsed as a valid protobuf message. */
    static String invalidProtobufPayloadWhileFetchingBlock(String reason) {
        return "Invalid protobuf payload while fetching block: " + reason;
    }

    /** Message used when the sequencer returns a block number different from the one the node expected. */
    static String unexpectedBlockNumber(int expectedBlockNumber, int actualBlockNumber) {
        return "Expected block " + expectedBlockNumber +
            " but sequencer returned block " + actualBlockNumber;
    }

    /** Message used when the node receives a transaction type it does not know how to apply. */
    static String unsupportedTransactionType(String className) {
        return "Unsupported transaction type: " + className;
    }

    /** Message used when the node cannot verify the signature of an unsupported transaction type. */
    static String cannotVerifyUnsupportedTransactionType(String className) {
        return "Cannot verify signature: unsupported transaction type " + className;
    }

    /** Message used when a signed transaction does not match the claimed user identity. */
    static String invalidUserSignature(String userId, long requestId) {
        return "Invalid signature for userId=" + userId +
            " requestId=" + requestId;
    }

    /** Message used when the node cannot load the AES key required to talk to the sequencer. */
    static String FailedToLoadSequencerAESKeyMessage(String path){
        return "Failed to load sequencer AES key — check that '" + path + "' exists on the classpath";
    }

    /** Message used when a duplicated request identifier is reported back to the client. */
    static String duplicatedTransactionRequest(long requestId, String duplicateSummary) {
        return "Duplicated transaction requestId: " + requestId +
            ". " + duplicateSummary;
    }
}
