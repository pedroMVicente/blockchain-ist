package pt.tecnico.blockchainist.client.domain.message;

/**
 * Centralized log messages used throughout the client domain and gRPC layer.
 */
public interface LogMessage {

    /** Message used when a request is completed successfully. */
    String REQUEST_COMPLETED = "request is completed";

    /** Message used when the client starts shutting down its channel to a node. */
    static String shuttingDownChannel(String nodeAddress) {
        return "Shutting down channel to node=" + nodeAddress;
    }

    /** Message used when a create-wallet request is sent to a node. */
    static String createWalletRequestSent(String nodeAddress, String userId, String walletId) {
        return "createWallet request sent node=" + nodeAddress +
            " userId=" + userId +
            " walletId=" + walletId;
    }

    /** Message used when a delete-wallet request is sent to a node. */
    static String deleteWalletRequestSent(String nodeAddress, String userId, String walletId) {
        return "deleteWallet request sent node=" + nodeAddress +
            " userId=" + userId +
            " walletId=" + walletId;
    }

    /** Message used when a transfer request is sent to a node. */
    static String transferRequestSent(
        String nodeAddress,
        String sourceUserId,
        String sourceWalletId,
        String destinationWalletId,
        long amount
    ) {
        return "transfer request sent node=" + nodeAddress +
            " srcUserId=" + sourceUserId +
            " srcWalletId=" + sourceWalletId +
            " dstWalletId=" + destinationWalletId +
            " value=" + amount;
    }

    /** Message used when a read-balance request is sent to a node. */
    static String readBalanceRequestSent(String nodeAddress, String walletId) {
        return "readBalance request sent node=" + nodeAddress + " walletId=" + walletId;
    }

    /** Message used when a read-balance response is received from a node. */
    static String readBalanceResponseReceived(String walletId, long balance) {
        return "readBalance response received walletId=" + walletId +
            " balance=" + balance;
    }

    /** Message used when a blockchain-state request is sent to a node. */
    static String getBlockchainStateRequestSent(String nodeAddress) {
        return "getBlockchainState request sent node=" + nodeAddress;
    }

    /** Message used when a blockchain-state response is received from a node. */
    static String getBlockchainStateResponseReceived(int txCount) {
        return "getBlockchainState response received txCount=" + txCount;
    }

    /** Message used when an RPC to a node fails with a gRPC status. */
    static String rpcError(String operation, String code, String description) {
        return operation + " rpc error code=" + code +
            " description=" + description;
    }

    /** Message used when a client user identifier is rejected before loading local key material. */
    static String rejectedInvalidUserIdentifier(String user) {
        return "Rejected invalid user identifier: " + user;
    }

    /** Message used when the RSA KeyFactory algorithm is unavailable while loading a client private key. */
    static String rsaKeyFactoryUnavailable(String reason) {
        return "RSA KeyFactory algorithm is unavailable on this JVM: " + reason;
    }

    /** Message used when a client's RSA private key file is malformed or invalid. */
    static String invalidOrMalformedRsaPrivateKeyForUser(String user, String reason) {
        return "Invalid or malformed RSA private key for user: " + user + ": " + reason;
    }

    /** Message used when a client's RSA private key file cannot be read from the classpath. */
    static String failedToReadPrivateKeyForUser(String user, String reason) {
        return "Failed to read private key for user: " + user + ": " + reason;
    }
}