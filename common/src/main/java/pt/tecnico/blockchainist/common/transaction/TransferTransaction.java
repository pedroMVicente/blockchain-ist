package pt.tecnico.blockchainist.common.transaction;

/**
 * Represents a transfer transaction.
 * <p>
 * Records that {@code value} units were transferred from the source wallet
 * ({@code srcWalletId}, owned by {@code srcUserId}) to the destination wallet
 * ({@code dstWalletId}).
 * </p>
 */
public class TransferTransaction extends InternalTransaction {

    private String dstWalletId;
    private long value;

    /**
     * Constructs a {@code TransferTransaction}.
     *
     * @param srcUserId    the identifier of the user initiating the transfer
     * @param srcWalletId  the identifier of the source wallet
     * @param dstWalletId  the identifier of the destination wallet
     * @param value        the amount to transfer
     */
    public TransferTransaction(long requestId, String srcUserId, String srcWalletId, String dstWalletId, long value) {
        super(requestId, srcUserId, srcWalletId);
        this.dstWalletId = dstWalletId;
        this.value = value;
    }

    /**
     * Returns the identifier of the destination wallet.
     *
     * @return the destination wallet ID
     */
    public String getDstWalletId() {
        return dstWalletId;
    }

    /**
     * Returns the amount transferred.
     *
     * @return the transfer value
     */
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "transfer {\n  src_userId: \"" + getUserId() + "\"\n  src_walletId: \"" + getWalletId() + "\"\n  dst_walletId: \"" + dstWalletId + "\"\n  value: " + value + "\n}\n";
    }

}
