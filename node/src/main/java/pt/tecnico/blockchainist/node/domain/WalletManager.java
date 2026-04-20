package pt.tecnico.blockchainist.node.domain;

import java.util.Map;
import java.util.TreeMap;

import pt.tecnico.blockchainist.node.domain.exceptions.BalanceIsNotZeroException;
import pt.tecnico.blockchainist.node.domain.exceptions.DestinationWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.IncorrectOwnerException;
import pt.tecnico.blockchainist.node.domain.exceptions.InitialStateCouldNotBeCreatedException;
import pt.tecnico.blockchainist.node.domain.exceptions.NonPositiveTransferAmountException;
import pt.tecnico.blockchainist.node.domain.exceptions.SourceWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.SparseBalanceFromOriginException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletAlreadyExistsException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;

/**
 * Manages all wallets and their balances for a single blockchain node.
 *
 * <p><strong>Thread safety: this class is NOT thread-safe.</strong>
 * It is designed to be accessed exclusively through {@link NodeState},
 * which serializes all calls via its own instance monitor ({@code synchronized}
 * methods). Never call methods of this class directly from outside
 * {@link NodeState}, and never hold a reference to this object across
 * threads without external synchronization.
 *
 * <p>All compound operations are safe only
 * because the caller ({@link NodeState}) guarantees that no two threads
 * execute concurrently inside this class.
 */
class WalletManager {

   /**
     * Maps wallet IDs to their corresponding {@link Wallet} instances.
     * {@link TreeMap} is used to keep wallets in a consistent alphabetical
     * order, which makes state snapshots deterministic across nodes.
     * Thread safety is delegated entirely to {@link NodeState}'s monitor.
     */
    private final Map<String, Wallet> walletsIdMap;

    /**
     * Constructs a new WalletManager with an empty wallet registry and
     * creates the central bank (BC) wallet with its initial balance.
     *
     * <p>Must only be called from within {@link NodeState}'s constructor,
     * before the {@link NodeState} instance is published to other threads.
     *
     * @throws InitialStateCouldNotBeCreatedException if the BC wallet
     *         cannot be created (should never happen in practice)
     */
    WalletManager()
    throws
        InitialStateCouldNotBeCreatedException
    {
        this.walletsIdMap = new TreeMap<>();
        try {
            this.addWallet(new Wallet(
                Wallet.INITIAL_WALLET_ID,
                Wallet.INITIAL_WALLET_OWNER,
                Wallet.BLOCKCHAIN_INITIAL_BALANCE
            ));
        } catch (WalletAlreadyExistsException e) {
            throw new InitialStateCouldNotBeCreatedException(
                ErrorMessage.InitialStateCouldNotBeCreatedMessage
            );
        }
    }

    /**
     * Registers a new wallet in the system.
     *
     * <p>Caller must hold the {@link NodeState} monitor.
     *
     * @param wallet the wallet to add
     * @throws WalletAlreadyExistsException if a wallet with the same ID already exists
     */
    void addWallet(
        Wallet wallet
    ) throws
        WalletAlreadyExistsException
    {
        if (this.walletsIdMap.containsKey(wallet.getId())) {
            throw new WalletAlreadyExistsException(
                ErrorMessage.WalletAlreadyExistsMessage
            );
        }
        this.walletsIdMap.put(wallet.getId(), wallet);
    }

    /**
     * Validates that a wallet can be deleted (exists, zero balance, correct owner)
     * and marks it as unavailable. Does NOT remove the wallet from the registry —
     * actual removal happens via {@link #removeWallet} when the sequencer
     * confirms the deletion.
     *
     * <p>Caller must hold the {@link NodeState} monitor.
     *
     * @param walletId the ID of the wallet to mark for deletion
     * @param userId   the ID of the user requesting removal
     * @throws WalletDoesNotExistException if no wallet with the given ID exists
     * @throws BalanceIsNotZeroException   if the wallet's balance is not zero
     * @throws IncorrectOwnerException     if the given user does not own the wallet
     */
    void removeWalletPending(
        String walletId,
        String userId
    ) throws
        WalletDoesNotExistException,
        BalanceIsNotZeroException,
        IncorrectOwnerException
    {
        // Single get() instead of containsKey() + get() — avoids double lookup
        // and eliminates any ambiguity about the wallet disappearing between the two calls.
        Wallet wallet = this.walletsIdMap.get(walletId);

        if (wallet == null) {
            throw new WalletDoesNotExistException(
                ErrorMessage.WalletDoesNotExistMessage
            );
        }
        if (wallet.getBalance() != Wallet.ZERO_BALANCE) {
            throw new BalanceIsNotZeroException(
                ErrorMessage.BalanceIsNotZeroMessage
            );
        }
        if (!wallet.getOwner().equals(userId)) {
            throw new IncorrectOwnerException(
                ErrorMessage.IncorrectOwnerIdMessage
            );
        }

        wallet.setDeletePending(true);
    }

    /**
     * Unconditionally removes a wallet from the registry. Called when the
     * sequencer confirms a delete, or when a speculative create needs to
     * be rolled back.
     *
     * @param walletId the ID of the wallet to remove
     */
    void removeWallet(String walletId) {
        this.walletsIdMap.remove(walletId);
    }

    /**
     * Validates that a transfer can proceed: both wallets exist, the user
     * owns the source wallet, the amount is strictly positive, and the
     * source wallet has sufficient balance.
     *
     * <p>Caller must hold the {@link NodeState} monitor.
     *
     * @param srcUserId   the ID of the user initiating the transfer (must own the source wallet)
     * @param srcWalletId the ID of the wallet to debit
     * @param dstWalletId the ID of the wallet to credit
     * @param amount      the amount to transfer (must be strictly positive)
     * @throws SourceWalletDoesNotExistException      if the source wallet does not exist
     * @throws DestinationWalletDoesNotExistException if the destination wallet does not exist
     * @throws IncorrectOwnerException                if the user does not own the source wallet
     * @throws NonPositiveTransferAmountException     if the amount is zero or negative
     * @throws SparseBalanceFromOriginException       if the source wallet lacks sufficient balance
     */
    void validateTransfer(
        String srcUserId,
        String srcWalletId,
        String dstWalletId,
        long amount
    ) throws
        SourceWalletDoesNotExistException,
        DestinationWalletDoesNotExistException,
        IncorrectOwnerException,
        NonPositiveTransferAmountException,
        SparseBalanceFromOriginException
    {
        Wallet srcWallet = this.walletsIdMap.get(srcWalletId);
        if (srcWallet == null) {
            throw new SourceWalletDoesNotExistException(
                ErrorMessage.SourceWalletDoesNotExistMessage
            );
        }

        Wallet dstWallet = this.walletsIdMap.get(dstWalletId);
        if (dstWallet == null) {
            throw new DestinationWalletDoesNotExistException(
                ErrorMessage.DestinationWalletDoesNotExistMessage
            );
        }

        if (!srcWallet.getOwner().equals(srcUserId)) {
            throw new IncorrectOwnerException(
                ErrorMessage.IncorrectOwnerIdMessage
            );
        }
        if (amount <= 0) {
            throw new NonPositiveTransferAmountException(
                ErrorMessage.NonPositiveTransferAmountMessage
            );
        }
        if (srcWallet.getBalance() < amount) {
            throw new SparseBalanceFromOriginException(
                ErrorMessage.SparseBalanceFromOriginMessage
            );
        }
    }

    /**
     * Moves {@code amount} from the source wallet to the destination wallet.
     * The caller must have already validated the transfer via
     * {@link #validateTransfer} and checked sufficient balance.
     *
     * <p>Caller must hold the {@link NodeState} monitor.
     */
    void executeTransfer(String srcWalletId, String dstWalletId, long amount) {
        Wallet srcWallet = this.walletsIdMap.get(srcWalletId);
        Wallet dstWallet = this.walletsIdMap.get(dstWalletId);
        srcWallet.decreaseBalance(amount);
        dstWallet.increaseBalance(amount);
    }

    /**
     * Returns the current balance of the wallet with the given ID.
     *
     * <p>Caller must hold the {@link NodeState} monitor.
     *
     * @param walletId the ID of the wallet to query
     * @return the wallet's current balance
     * @throws WalletDoesNotExistException if no wallet with the given ID exists
     */
    long getWalletBalance(
        String walletId
    )
    throws
        WalletDoesNotExistException
    {
        Wallet wallet = this.walletsIdMap.get(walletId);
        if (wallet == null) {
            throw new WalletDoesNotExistException(
                ErrorMessage.WalletDoesNotExistMessage
            );
        }
        return wallet.getBalance();
    }

    /**
     * Returns the wallet with the given ID, or {@code null} if it does not exist.
     *
     * @param walletId the ID of the wallet to look up
     * @return the wallet, or {@code null}
     */
    Wallet getWallet(String walletId) {
        return this.walletsIdMap.get(walletId);
    }
}
