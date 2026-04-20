package pt.tecnico.blockchainist.common.transaction;

/**
 * Represents a create-wallet transaction.
 * <p>
 * Records that a new wallet identified by {@code walletId} was created for the
 * user identified by {@code userId}.
 * </p>
 */
public class CreateWalletTransaction extends InternalTransaction {

    /**
     * Constructs a {@code CreateWalletTransaction}.
     *
     * @param userId   the identifier of the user who owns the new wallet
     * @param walletId the identifier of the wallet to create
     */
    public CreateWalletTransaction(long requestId, String userId, String walletId) {
        super(requestId, userId, walletId);
    }

    @Override
    public String toString() {
        return "create_wallet {\n  userId: \"" + getUserId() + "\"\n  walletId: \"" + getWalletId() + "\"\n}\n";
    }

}
