package pt.tecnico.blockchainist.node.domain;

/**
 * Represents a wallet in the blockchain system.
 * Each wallet has a unique ID, an owner, a balance, and metadata used
 * by the node's optimistic execution logic.
 */
public class Wallet {

    /** The initial balance assigned to every newly created wallet. */
    public final static long INITIAL_WALLET_VALUE = 0;

    /** Constant representing a zero balance, used for balance validation checks. */
    public final static long ZERO_BALANCE = 0;

    /** The central bank (BC) user identifier. */
    public final static String INITIAL_WALLET_OWNER = "BC";

    /** The central bank (BC) wallet identifier. */
    public final static String INITIAL_WALLET_ID = "bc";

    /** The initial balance of the central bank wallet. */
    public final static long BLOCKCHAIN_INITIAL_BALANCE = 1000;

    /** The unique identifier of this wallet. */
    private String id;

    /** The identifier of the owner of this wallet. */
    private String owner;

    /** The current balance of this wallet. */
    private long balance;

    /**
     * Indicates whether there has been a deleteWallet request for this
     * wallet that is pending confirmation on the blockchain.
     */
    private boolean deletePending = false;

    /**
     * The requestId of the most recent transaction that touched this wallet.
     * Used to compute dependency chains: a new transaction on this wallet
     * will declare a dependency on this requestId. {@code null} if no
     * transaction has touched this wallet since the last block replay.
     */
    private Long latestPendingRequestId;

    /**
     * Constructs a new Wallet with the given ID, owner, balance.
     *
     * @param id        the unique identifier for this wallet
     * @param owner     the owner identifier associated with this wallet
     * @param balance   the initial balance
     */
    public Wallet(String id, String owner, long balance) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
        this.latestPendingRequestId = null;
    }

    /**
     * Returns the unique identifier of this wallet.
     *
     * @return the wallet ID
     */
    public String getId() { return this.id; }

    /**
     * Returns the owner identifier of this wallet.
     *
     * @return the wallet owner
     */
    public String getOwner() { return this.owner; }

    /**
     * Returns the current balance of this wallet.
     *
     * @return the wallet balance
     */
    public long getBalance() { return this.balance; }

    /**
     * Decreases the wallet balance by the given value.
     * Should only be called after validating that the balance is sufficient.
     *
     * @param value the amount to subtract from the balance
     */
    public void decreaseBalance(long value) {
        this.balance -= value;
    }

    /**
     * Increases the wallet balance by the given value.
     *
     * @param value the amount to add to the balance
     */
    public void increaseBalance(long value) {
        this.balance += value;
    }

    public boolean hasDeletePending() {
        return this.deletePending;
    }

    public void setDeletePending(boolean deletePending) {
        this.deletePending = deletePending;
    }

    public Long getLatestPendingRequestId() {
        return this.latestPendingRequestId;
    }

    public void setLatestPendingRequestId(Long requestId) {
        this.latestPendingRequestId = requestId;
    }
}
