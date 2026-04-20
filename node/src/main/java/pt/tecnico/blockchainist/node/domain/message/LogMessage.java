package pt.tecnico.blockchainist.node.domain.message;

/**
 * Centralized log messages used throughout the node domain and gRPC layer.
 */
public interface LogMessage {

    /** Message used when the sequencer public key is loaded successfully. */
    String PublicKeyLoadedSuccessfully = "Public key loaded successfully.";

    /** Message used when an interceptor-imposed delay is interrupted. */
    String DelayInterruptedMessage = "Delay interrupted.";

    /** Message used when a blockchain-state request is received. */
    String GetBlockchainStateReceivedMessage = "getBlockchainState received";

    /** Message used when a blockchain-state call is interrupted while waiting for initialization. */
    String GetBlockchainStateInterruptedWhileWaitingForInitializationMessage =
        "getBlockchainState interrupted while waiting for initialization";

    /** Message used when the authorized users for one organization are loaded successfully. */
    static String organizationUsersFetchedSuccessfully(String organization, Object users) {
        return "Users fetched successfully. Organization '" + organization + "': " + users;
    }

    /** Message used when the RSA KeyFactory algorithm is unavailable while loading the sequencer public key. */
    static String rsaKeyFactoryUnavailable(String reason) {
        return "RSA KeyFactory algorithm is unavailable on this JVM: " + reason;
    }

    /** Message used when the sequencer public key file is malformed or invalid. */
    static String invalidOrMalformedSequencerPublicKey(String reason) {
        return "publicSequencer.der contains an invalid or malformed RSA key: " + reason;
    }

    /** Message used when the sequencer public key file cannot be read from the classpath. */
    static String failedToReadSequencerPublicKey(String reason) {
        return "Failed to read publicSequencer.der from classpath: " + reason;
    }

    /** Message used when an unexpected runtime error happens in the block fetch loop. */
    static String unexpectedErrorWhileProcessingBlock(String reason) {
        return "Unexpected error while processing block: " + reason;
    }

    /** Message used when a fetched block is dropped because RSA signature verification failed. */
    static String droppingBlockInvalidSignature(int blockNumber) {
        return "Dropping block " + blockNumber + ": RSA signature verification failed.";
    }

    /** Message used when a block is received successfully from the sequencer. */
    static String receivedBlock(int blockNumber, int txCount) {
        return "Received block=" + blockNumber + " txCount=" + txCount;
    }

    /** Message used when a request is skipped because it already has a canonical outcome. */
    static String skippingAlreadyCompletedRequest(long requestId) {
        return "Skipping already-completed requestId=" + requestId;
    }

    /** Message used when a gRPC fetch of a block from the sequencer fails. */
    static String grpcErrorFetchingBlock(int blockNumber, String code, String description) {
        return "gRPC error fetching block=" + blockNumber +
            " code=" + code +
            " description=" + description;
    }

    /** Message used when user-signature verification fails for one transaction. */
    static String signatureVerificationFailedRequest(long requestId, String reason) {
        return "Signature verification failed requestId=" + requestId +
            " reason=" + reason;
    }

    /** Message used when an optimistic transfer is skipped during canonical replay. */
    static String skippingAlreadyAppliedOptimisticTransfer(long requestId) {
        return "Skipping already-applied optimistic transfer requestId=" + requestId;
    }

    /** Message used when a canonical transaction is applied successfully. */
    static String appliedCanonicalTransaction(String txType) {
        return "Applied canonical txType=" + txType;
    }

    /** Message used when canonical application of a transaction fails. */
    static String failedCanonicalRequest(long requestId, String txType, String reason) {
        return "Failed canonical requestId=" + requestId +
            " txType=" + txType +
            " reason=" + reason;
    }

    /** Message used when RSA verification fails because the public key is invalid. */
    static String invalidVerificationPublicKey(String reason) {
        return "Signature verification failed: public key is invalid or incompatible: " + reason;
    }

    /** Message used when RSA verification fails because the algorithm is unavailable. */
    static String verificationAlgorithmUnavailable(String reason) {
        return "Signature verification failed: SHA256withRSA algorithm unavailable: " + reason;
    }

    /** Message used when RSA verification fails because the signature bytes are malformed. */
    static String malformedSignatureBytes(String reason) {
        return "Signature verification failed: signature bytes are malformed: " + reason;
    }

    /** Message used when checking whether the sequencer AES key is missing during debugging. */
    static String sequencerAesKeyIsNull(boolean isNull) {
        return "sequencerAESkey null=" + isNull;
    }

    /** Message used when logging the IV length of an encrypted request during debugging. */
    static String ivLength(int length) {
        return "iv length=" + length;
    }

    /** Message used when logging the encrypted payload length during debugging. */
    static String payloadLength(int length) {
        return "payload length=" + length;
    }

    /** Message used when checking whether the decrypted byte array is null during debugging. */
    static String decryptedBytesAreNull(boolean isNull) {
        return "decryptedBytes null=" + isNull;
    }

    /** Message used when an RPC call is intentionally delayed by the node interceptor. */
    static String delayingCallBySeconds(int delaySeconds) {
        return "Delaying call by " + delaySeconds + " second(s).";
    }

    /** Message used when an invalid delay header value is ignored by the node interceptor. */
    static String invalidDelayValueIgnored(String delayValue) {
        return "Invalid delay value ignored: " + delayValue;
    }

        /** Message used when a create-wallet request is received by the node service. */
    static String createWalletReceived(long requestId, String userId, String walletId) {
        return "createWallet received requestId=" + requestId +
            " userId=" + userId +
            " walletId=" + walletId;
    }

    /** Message used when a delete-wallet request is received by the node service. */
    static String deleteWalletReceived(long requestId, String userId, String walletId) {
        return "deleteWallet received requestId=" + requestId +
            " userId=" + userId +
            " walletId=" + walletId;
    }

    /** Message used when a transfer request is received by the node service. */
    static String transferReceived(
        long requestId,
        String srcUserId,
        String srcWalletId,
        String dstWalletId,
        long value
    ) {
        return "transfer received id=" + requestId +
            " srcUserId=" + srcUserId +
            " srcWalletId=" + srcWalletId +
            " dstWalletId=" + dstWalletId +
            " value=" + value;
    }

    /** Message used when a read-balance request is received. */
    static String readBalanceReceived(String walletId) {
        return "readBalance received walletId=" + walletId;
    }

    /** Message used when a read-balance call is interrupted while waiting for initialization. */
    String ReadBalanceInterruptedWhileWaitingForInitializationMessage =
        "readBalance interrupted while waiting for initialization";

    /** Message used when a read-balance response is returned successfully. */
    static String readBalanceResponseReturned(String walletId, long balance) {
        return "readBalance response returned walletId=" + walletId + " balance=" + balance;
    }

    /** Message used when read-balance fails because the wallet does not exist. */
    static String readBalanceDomainError(String walletId, String reason) {
        return "readBalance domain error walletId=" + walletId + " message=" + reason;
    }

    /** Message used when a blockchain-state response is returned successfully. */
    static String getBlockchainStateResponseReturned(int txCount) {
        return "getBlockchainState response returned txCount=" + txCount;
    }

    /** Message used when a user identifier is rejected because it is invalid or unsafe for key lookup. */
    static String rejectedInvalidUserIdentifier(String user) {
        return "Rejected invalid user identifier: " + user;
    }

    /** Message used when a user's RSA public key is malformed or invalid. */
    static String invalidOrMalformedRsaPublicKeyForUser(String user, String reason) {
        return "Invalid or malformed RSA public key for user: " + user + ": " + reason;
    }

    /** Message used when a user's RSA public key cannot be read from the classpath. */
    static String failedToReadPublicKeyForUser(String user, String reason) {
        return "Failed to read public key for user: " + user + ": " + reason;
    }

    /** Message used when a create-wallet broadcast is accepted by the sequencer. */
    static String createWalletBroadcastAccepted(long requestId) {
        return "createWallet broadcast accepted requestId=" + requestId;
    }

    /** Message used when a create-wallet response is returned successfully. */
    static String createWalletResponseReturned(String walletId) {
        return "createWallet response returned walletId=" + walletId;
    }

    /** Message used when a delete-wallet broadcast is accepted by the sequencer. */
    static String deleteWalletBroadcastAccepted(long requestId) {
        return "deleteWallet broadcast accepted requestId=" + requestId;
    }

    /** Message used when a delete-wallet response is returned successfully. */
    static String deleteWalletResponseReturned(String walletId) {
        return "deleteWallet response returned walletId=" + walletId;
    }

    /** Message used when an optimistic transfer returns success before sequencer confirmation. */
    static String transferOptimisticResponseReturned(long requestId) {
        return "transfer optimistic response returned requestId=" + requestId;
    }

    /** Message used when the sequencer broadcast for an optimistic transfer fails. */
    static String transferOptimisticBroadcastFailed(long requestId, String code) {
        return "transfer optimistic broadcast failed requestId=" + requestId +
            " code=" + code;
    }

    /** Message used when a transfer broadcast is accepted by the sequencer. */
    static String transferBroadcastAccepted(long requestId) {
        return "transfer broadcast accepted requestId=" + requestId;
    }

    /** Message used when a transfer response is returned successfully. */
    static String transferResponseReturned(String srcWalletId) {
        return "transfer response returned srcWalletId=" + srcWalletId;
    }

    /** Message used when the sequencer rejects a broadcast because the requestId is already known. */
    static String duplicateRejectedBySequencer(String operation, long requestId) {
        return operation + " duplicate rejected by sequencer requestId=" + requestId;
    }

    /** Message used when a gRPC failure happens during one processing phase of a transactional request. */
    static String grpcError(
        String operation,
        String phase,
        String code,
        String description,
        String details
    ) {
        return operation + " grpc error phase=" + phase +
            " code=" + code +
            " description=" + description +
            " " + details;
    }
}