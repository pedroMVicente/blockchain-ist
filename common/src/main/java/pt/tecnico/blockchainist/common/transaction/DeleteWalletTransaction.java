package pt.tecnico.blockchainist.common.transaction;

/**
 * Represents a delete-wallet transaction.
 * <p>
 * Records that the wallet identified by {@code walletId}, owned by the user
 * identified by {@code userId}, was deleted.
 * </p>
 */
public class DeleteWalletTransaction extends InternalTransaction {

    /**
     * Constructs a {@code DeleteWalletTransaction}.
     *
     * @param userId   the identifier of the user who owns the wallet
     * @param walletId the identifier of the wallet to delete
     */
    public DeleteWalletTransaction(long requestId, String userId, String walletId) {
        super(requestId, userId, walletId);
    }

    @Override
    public String toString() {
        return "delete_wallet {\n  userId: \"" + getUserId() + "\"\n  walletId: \"" + getWalletId() + "\"\n}\n";
    }

}
