package pt.tecnico.blockchainist.node.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import pt.tecnico.blockchainist.common.transaction.*;
import pt.tecnico.blockchainist.node.domain.block.NodeBlock;
import pt.tecnico.blockchainist.node.domain.exceptions.BalanceIsNotZeroException;
import pt.tecnico.blockchainist.node.domain.exceptions.DestinationWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.IncorrectOwnerException;
import pt.tecnico.blockchainist.node.domain.exceptions.NonPositiveTransferAmountException;
import pt.tecnico.blockchainist.node.domain.exceptions.SourceWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.SparseBalanceFromOriginException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletAlreadyExistsException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.InitialStateCouldNotBeCreatedException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;


/**
 * Holds the complete local state of a blockchain node.
 *
 * State is composed of:
 *   - Wallet state: the set of wallets and their balances, managed by {@link WalletManager}.
 *   - Transaction ledger: an ordered list of every block delivered by the sequencer.
 *   - Request tracking: pending and completed request outcomes.
 *   - Optimistic execution tracking: which requests were applied optimistically
 *     (so BlockFetcher can skip re-applying them).
 *
 * Thread safety:
 *   All public methods are {@code synchronized} on the same monitor (this instance).
 *   Write operations that mutate wallet state and append to the ledger are
 *   combined into single synchronized methods to ensure that wallet state and
 *   ledger are never observable in an inconsistent intermediate state by another thread.
 *
 * Locking contract:
 *   {@link WalletManager} is NOT thread-safe on its own. All access to it
 *   is mediated exclusively through the {@code synchronized} methods of this
 *   class. No code outside this class may hold a reference to the
 *   {@link WalletManager} instance or call its methods directly.
 */
public class NodeState {
	private final String organization;
	private final WalletManager walletManager;
	private final List<NodeBlock> blockchain;

	private final Map<Long, RequestOutcome> completedRequestResults;
	private final Map<Long, CompletableFuture<RequestOutcome>> pendingRequestFutures;
	private volatile boolean isFullyInitialized = false;
	private final Object fullyInitializedMonitor = new Object();
	private volatile boolean sequencerJustStarted = true;

	/**
	 * RequestIds of transfers that were optimistically applied to local state
	 * (balance already mutated). BlockFetcher skips re-application for these.
	 */
	private final Set<Long> optimisticallyAppliedRequests;

	/**
	 * Creates a new node state for the given organization and initializes the
	 * local wallet registry, blockchain, and request-tracking structures.
	 *
	 * @param organization the organization identifier served by this node
	 * @throws InitialStateCouldNotBeCreatedException if bootstrap state cannot be created
	 */
	public NodeState(String organization) throws InitialStateCouldNotBeCreatedException {
		this.organization = organization;
		this.walletManager = new WalletManager();
		this.blockchain = new ArrayList<>();
		this.completedRequestResults = new HashMap<>();
		this.pendingRequestFutures = new HashMap<>();
		this.optimisticallyAppliedRequests = new HashSet<>();
	}


	/** Returns the organization identifier for this node. */
	public String getOrganization() {
		return organization;
	}


	// -----------------------------------------------------------------------
	//  Blockchain / block tracking
	// -----------------------------------------------------------------------

	/**
	 * Returns a snapshot of the current transaction ledger.
	 *
	 * A defensive copy is returned so that callers can safely iterate the list
	 * without holding the monitor, even if another thread appends a transaction
	 * concurrently after this method returns.
	 *
	 * @return a new list containing all transactions applied so far, in order
	 */
	public synchronized List<InternalTransaction> getBlockchainState() {
		return this.blockchain.stream()
			.flatMap(block -> block.getTransactions().stream())
			.toList();
	}


	/**
	 * Returns the next block number that the node expects to receive from the sequencer.
	 *
	 * This is defined as the number of blocks already recorded locally.
	 *
	 * @return the next block number to fetch
	 */
	public synchronized int getNextBlockNumber() {
		return this.blockchain.size();
	}


	/**
	 * Records a block that has already been fetched and applied locally.
	 *
	 * @param block the next ordered block produced by the sequencer
	 * @throws IllegalStateException if the block number is not the expected next one
	 */
	public synchronized void recordProcessedBlock(NodeBlock block) {
        if (block.getBlockNumber() != this.blockchain.size()) {
            throw new IllegalStateException(
				ErrorMessage.ProcessedBlockOutOfOrder(this.blockchain.size(), block.getBlockNumber())
            );
        }

        this.blockchain.add(block);
    }


	// -----------------------------------------------------------------------
	//  Request lifecycle (deduplication + waiting)
	// -----------------------------------------------------------------------

	/**
	 * Checks whether the node already knows the final outcome of a request.
	 *
	 * @param requestId the request identifier to inspect
	 * @return {@code true} if a canonical outcome is already stored
	 */
    public synchronized boolean hasCompletedRequest(long requestId) {
        return completedRequestResults.containsKey(requestId);
    }


	/**
	 * Attempts to start processing a request by its unique identifier.
	 *
	 * @param requestId the request identifier being submitted
	 * @return whether the request is new, already completed, or still pending
	 */
	public synchronized StartRequestResult startRequest(long requestId) {
		if (completedRequestResults.containsKey(requestId)) {
			return StartRequestResult.duplicateCompleted(
				completedRequestResults.get(requestId)
			);
		}

		if (pendingRequestFutures.containsKey(requestId)) {
			return StartRequestResult.duplicatePending();
		}

		pendingRequestFutures.put(requestId, new CompletableFuture<>());
		return StartRequestResult.started();
	}


	/**
	 * Stores the canonical outcome of a request and wakes any waiters.
	 *
	 * @param requestId the completed request identifier
	 * @param outcome the canonical success or failure result
	 */
    public synchronized void recordCompletedRequest(long requestId, RequestOutcome outcome) {
        completedRequestResults.putIfAbsent(requestId, outcome);

		CompletableFuture<RequestOutcome> future = pendingRequestFutures.remove(requestId);
		if (future != null) {
			future.complete(outcome);
		}
    }


	/**
	 * Removes a request from the pending set when it can no longer complete.
	 *
	 * @param requestId the request identifier to abandon
	 */
    public synchronized void abandonRequest(long requestId) {
		CompletableFuture<RequestOutcome> future = pendingRequestFutures.remove(requestId);
		if (future != null) {
			future.completeExceptionally(
				new IllegalStateException(
					ErrorMessage.RequestAbandoned(requestId)
				)
			);
		}
    }


	/**
	 * Waits until a request transitions from pending to completed.
     *
     * The NodeState monitor is released before blocking so that other threads
     * can still call synchronized methods (e.g. to apply the transaction and
     * call {@link #recordCompletedRequest}) without deadlocking.
	 *
	 * @param requestId the request identifier to await
	 * @return the canonical outcome recorded for the request
	 * @throws InterruptedException if the waiting thread is interrupted
	 * @throws IllegalStateException if the request is neither pending nor completed
	 */
    public RequestOutcome waitForRequestResult(long requestId) throws InterruptedException {
        CompletableFuture<RequestOutcome> future;
    	RequestOutcome completedOutcome;

		synchronized (this) {
			completedOutcome = completedRequestResults.get(requestId);
			if (completedOutcome != null) {
				return completedOutcome;
			}
			future = pendingRequestFutures.get(requestId);
			if (future == null) {
            	throw new IllegalStateException(
					ErrorMessage.RequestNeitherPendingNorCompleted(requestId)
            	);
			}
		}

		try {
			return future.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IllegalStateException illegalState) {
				throw illegalState;
			}
			throw new IllegalStateException(
				ErrorMessage.UnexpectedErrorWhileWaitingForRequestResult(requestId),
				cause
			);
		}
    }


	// -----------------------------------------------------------------------
	//  Dependency computation
	// -----------------------------------------------------------------------

	/**
	 * Computes the dependency list for a transaction that touches the given wallets,
	 * and updates each wallet's {@code latestPendingRequestId} to the new requestId.
	 *
	 * <p>Must be called under the NodeState monitor (i.e. from a synchronized method).
	 *
	 * @param requestId the requestId of the new transaction
	 * @param walletIds the wallet IDs involved in this transaction
	 * @return a list of at most {@code walletIds.length} non-null dependency requestIds
	 */
	private List<Long> computeAndSetDependencies(long requestId, String... walletIds) {
		List<Long> dependencies = new ArrayList<>(walletIds.length);
		for (String walletId : walletIds) {
			Wallet wallet = walletManager.getWallet(walletId);
			if (wallet == null) continue;
			if (wallet.getLatestPendingRequestId() != null) {
				long dependency = wallet.getLatestPendingRequestId();
				if (!dependencies.contains(dependency)) {
					dependencies.add(dependency);
				}
			}
			wallet.setLatestPendingRequestId(requestId);
		}
		return dependencies;
	}


	// -----------------------------------------------------------------------
	//  Create wallet
	// -----------------------------------------------------------------------


	/**
	 * Creates a wallet as a canonical operation (called by BlockFetcher).
	 *
	 * @param userId the owner identifier
	 * @param walletId the wallet identifier to create
	 * @throws WalletAlreadyExistsException if a wallet with this ID already exists
	 */
	public synchronized void createWallet(
		String userId,
		String walletId
	) throws
		WalletAlreadyExistsException
	{
		this.walletManager.addWallet(new Wallet(walletId, userId, Wallet.INITIAL_WALLET_VALUE));
	}


	// -----------------------------------------------------------------------
	//  Delete wallet
	// -----------------------------------------------------------------------

	/**
	 * Validates and marks a wallet for deletion as part of a pending delete request.
	 * Delegates all validation (existence, zero balance, ownership) to
	 * {@link WalletManager#removeWalletPending}, which also sets {@code deletePending = true}.
	 * The caller must broadcast to the sequencer and wait for confirmation.
	 *
	 * @param requestId the requestId of the delete transaction
	 * @param walletId the wallet to mark for deletion
	 * @param userId the user requesting the deletion (must own the wallet)
	 * @return the dependency list for this transaction
	 */
	public synchronized List<Long> deleteWalletPending(
		long requestId,
		String walletId,
		String userId
	) throws
		WalletDoesNotExistException,
		BalanceIsNotZeroException,
		IncorrectOwnerException
	{
		walletManager.removeWalletPending(walletId, userId);
		List<Long> dependencies = computeAndSetDependencies(requestId, walletId);
		return dependencies;
	}

	/**
	 * Deletes a wallet as a canonical operation (called by BlockFetcher).
	 *
	 * <p>If the wallet exists with {@code deletePending = true}, it was already
	 * validated and marked by {@link #deleteWalletPending} — we just remove it.
	 * Otherwise we run full validation and remove it.
	 *
	 * @param userId the requesting owner identifier
	 * @param walletId the wallet identifier to delete
	 */
	public synchronized void deleteWallet(
		String userId,
		String walletId
	) throws
		WalletDoesNotExistException,
		BalanceIsNotZeroException,
		IncorrectOwnerException
	{
		Wallet wallet = walletManager.getWallet(walletId);
		if (wallet != null && wallet.hasDeletePending()) {
			walletManager.removeWallet(walletId);
			return;
		}
		this.walletManager.removeWalletPending(walletId, userId);
		this.walletManager.removeWallet(walletId);
	}

	/**
	 * Undoes a pending delete when the broadcast to the sequencer fails.
	 * Restores the wallet to {@code deletePending = false} so it becomes
	 * usable again.
	 *
	 * @param walletId the wallet whose deletion failed
	 */
	public synchronized void rollbackDeleteWallet(String walletId) {
		Wallet wallet = walletManager.getWallet(walletId);
		if (wallet != null) {
			wallet.setDeletePending(false);
		}
	}


	// -----------------------------------------------------------------------
	//  Transfer — optimistic check + execution
	// -----------------------------------------------------------------------

	/**
	 * Result of preparing a transfer: carries the dependencies and whether
	 * the transfer was executed optimistically.
	 */
	public static class TransferPreparation {
		private final List<Long> dependencies;
		private final boolean optimistic;

		TransferPreparation(List<Long> dependencies, boolean optimistic) {
			this.dependencies = dependencies;
			this.optimistic = optimistic;
		}

		public List<Long> getDependencies() { return dependencies; }
		public boolean isOptimistic() { return optimistic; }
	}


	/**
	 * Prepares a transfer by validating, computing dependencies and, if
	 * conditions allow, executing it optimistically on local state.
	 *
	 * <p>The source wallet must have sufficient balance ({@code balance >= amount})
	 * or the transfer is rejected immediately with a
	 * {@link SparseBalanceFromOriginException}.
	 *
	 * @param requestId the requestId of the transfer
	 * @param srcUserId the source user
	 * @param srcWalletId the source wallet
	 * @param dstWalletId the destination wallet
	 * @param amount the transfer amount
	 * @return a {@link TransferPreparation} with dependencies and optimistic flag
	 * @throws SourceWalletDoesNotExistException if the source wallet does not exist
	 * @throws DestinationWalletDoesNotExistException if the destination wallet does not exist
	 * @throws IncorrectOwnerException if the user does not own the source wallet
	 * @throws SparseBalanceFromOriginException if the source wallet lacks funds
	 * @throws NonPositiveTransferAmountException if the amount is not strictly positive
	 */
	public synchronized TransferPreparation prepareTransfer(
		long requestId,
		String srcUserId,
		String srcWalletId,
		String dstWalletId,
		long amount
	) throws
		SourceWalletDoesNotExistException,
		DestinationWalletDoesNotExistException,
		IncorrectOwnerException,
		SparseBalanceFromOriginException,
		NonPositiveTransferAmountException
	{
		walletManager.validateTransfer(srcUserId, srcWalletId, dstWalletId, amount);

		Wallet srcWallet = walletManager.getWallet(srcWalletId);
		Wallet dstWallet = walletManager.getWallet(dstWalletId);

		if (srcWallet.hasDeletePending()) {
			throw new SourceWalletDoesNotExistException(ErrorMessage.SourceWalletDoesNotExistMessage);
		}
		if (dstWallet.hasDeletePending()) {
			throw new DestinationWalletDoesNotExistException(ErrorMessage.DestinationWalletDoesNotExistMessage);
		}

		List<Long> dependencies = computeAndSetDependencies(requestId, srcWalletId, dstWalletId);

		if (OrganizationUsers.contains(dstWallet.getOwner())) {
			walletManager.executeTransfer(srcWalletId, dstWalletId, amount);
			optimisticallyAppliedRequests.add(requestId);
			return new TransferPreparation(dependencies, true);
		} else {
			return new TransferPreparation(dependencies, false);
		}
	}


	/**
	 * Applies a transfer as a confirmed canonical operation (from BlockFetcher).
	 * Used for transactions that were NOT optimistically applied.
	 *
	 * @param srcUserId the source user
	 * @param srcWalletId the source wallet
	 * @param dstWalletId the destination wallet
	 * @param amount the transfer amount
	 */
	public synchronized void transfer(
		String srcUserId,
		String srcWalletId,
		String dstWalletId,
		long amount
	) throws
        SourceWalletDoesNotExistException,
        DestinationWalletDoesNotExistException,
        IncorrectOwnerException,
        SparseBalanceFromOriginException,
        NonPositiveTransferAmountException
	{
		walletManager.validateTransfer(srcUserId, srcWalletId, dstWalletId, amount);
		walletManager.executeTransfer(srcWalletId, dstWalletId, amount);
	}

	// -----------------------------------------------------------------------
	//  Pending-set accessors (used by BlockFetcher)
	// -----------------------------------------------------------------------

	/**
	 * If the given requestId was in the optimistically-applied set, removes it
	 * and returns {@code true}. Otherwise returns {@code false}.
	 */
	public synchronized boolean removeFromOptimisticallyApplied(long requestId) {
		return optimisticallyAppliedRequests.remove(requestId);
	}


	// -----------------------------------------------------------------------
	//  Read balance
	// -----------------------------------------------------------------------

	/**
	 * Returns the current balance of the specified wallet. This reflects
	 * optimistic transfers that have already been applied locally.
	 *
	 * @param walletId the identifier of the wallet to query
	 * @return the current balance of the wallet
	 * @throws WalletDoesNotExistException if no wallet with {@code walletId} exists
	 */
	public synchronized long readBalance(
		String walletId
	) throws
		WalletDoesNotExistException
	{
		return this.walletManager.getWalletBalance(walletId);
	}

	/**
 	 * Marks the node as fully initialized and wakes any threads waiting for startup catch-up to finish.
	 *
	 * @return {@code true} if the node is fully initialized, {@code false} otherwise
	 */
	public void markFullyInitializedIfNotYet() {
		if (this.isFullyInitialized) {
			return;
		}
		synchronized (this.fullyInitializedMonitor) {
			this.isFullyInitialized = true;
			this.fullyInitializedMonitor.notifyAll();
		}
	}

	/**
	 * Waits until the node has finished replaying all historical blocks and is fully initialized.
	 *
	 * @throws InterruptedException if the waiting thread is interrupted
	 */
	public void waitIfNotFullyInitialized() throws InterruptedException {
		if (!this.isFullyInitialized) {
			synchronized (this.fullyInitializedMonitor) {
				while (!this.isFullyInitialized) {
					this.fullyInitializedMonitor.wait();
				}
			}
		}
	}

	/**
	 * Checks if the sequencer has just started and the node is still bootstrapping.
	 *
	 * @return true if the sequencer just started and the node is bootstrapping, false otherwise
	 */
	public boolean isBootstrapping() {
		return sequencerJustStarted && completedRequestResults.isEmpty() && pendingRequestFutures.isEmpty();
	}

	/**
	 * Sets the flag indicating whether the sequencer has just started and the node is bootstrapping.
	 *
	 * @param sequencerJustStarted true if the sequencer has just started, false otherwise
	 */
	public synchronized void setSequencerJustStarted(boolean sequencerJustStarted) {
	    this.sequencerJustStarted = sequencerJustStarted;
	}
}