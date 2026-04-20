package pt.tecnico.blockchainist.common.transaction;

/**
 * Base class for all internal transaction types.
 * <p>
 * Holds the common fields shared by every transaction: the identity of the user
 * initiating the operation ({@code userId}) and the wallet they are acting on
 * ({@code walletId}).
 * </p>
 * <p>
 * Concrete subclasses — {@link CreateWalletTransaction}, {@link DeleteWalletTransaction},
 * and {@link TransferTransaction} — extend this class with operation-specific fields.
 * </p>
 */
public abstract class InternalTransaction {

    private final long requestId;
    private final String userId;
    private final String walletId;

    /**
     * Constructs an {@code InternalTransaction} with the given user and wallet identifiers.
     *
     * @param userId   the identifier of the user initiating the transaction
     * @param walletId the identifier of the wallet being operated on
     */
    public InternalTransaction(long requestId, String userId, String walletId) {
        this.requestId = requestId;
        this.userId = userId;
        this.walletId = walletId;
    }


    /**
     * Returns the unique identifier of this transaction.
     *
     * @return the transaction ID
     */
    public long getRequestId() {
        return requestId;
    }

    /**
     * Returns the identifier of the user initiating the transaction.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Returns the identifier of the wallet being operated on.
     *
     * @return the wallet ID
     */
    public String getWalletId() {
        return walletId;
    }
    
}
