package pt.tecnico.blockchainist.sequencer.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;
import pt.tecnico.blockchainist.sequencer.domain.block.SequencerBlock;
import pt.tecnico.blockchainist.sequencer.domain.message.ErrorMessage;

/**
 * Holds the sequencer state needed to build globally ordered blocks and serve them to nodes.
 */
public class SequencerState {
    private static final int INITIAL_BLOCK_VALUE = 0;
    private static final int INITIAL_SEQUENCE_VALUE = 0;
    private static final int NO_CLOSED_BLOCKS = -1;

    private final Map<Integer, SequencerBlock> closedBlocks;
    private final Set<Long> seenRequestIds;

    /** Transactions waiting for unresolved dependencies before they can be sequenced. */
    private final Map<Long, BufferedTransaction> bufferedTransactions;

    /**
     * Reverse index: maps a dependency requestId to all buffered transactions that
     * are waiting on it. When a requestId is sequenced, this map is consulted to
     * find and potentially unblock waiting transactions.
     */
    private final Map<Long, List<BufferedTransaction>> dependencyWaiters;

    private SequencerBlock currentBlock;
    private int blockCounter;
    private int sequenceCounter;
    private boolean shuttingDown;

    /**
     * A transaction whose dependencies have not all been sequenced yet.
     */
    private static class BufferedTransaction {
        final SignedInternalTransaction transaction;
        final Set<Long> unresolvedDependencies;

        BufferedTransaction(SignedInternalTransaction transaction, Set<Long> unresolvedDependencies) {
            this.transaction = transaction;
            this.unresolvedDependencies = unresolvedDependencies;
        }
    }

    /**
     * Creates an empty sequencer state with no closed blocks and no open block.
     */
    public SequencerState() {
        this.blockCounter = INITIAL_BLOCK_VALUE;
        this.sequenceCounter = INITIAL_SEQUENCE_VALUE;
        this.closedBlocks = new ConcurrentHashMap<>();
        this.seenRequestIds = new HashSet<>();
        this.bufferedTransactions = new HashMap<>();
        this.dependencyWaiters = new HashMap<>();
        this.currentBlock = null;
        this.shuttingDown = false;
    }

    /**
     * Opens a new current block when no block is currently accepting transactions.
     */
    private void openBlockIfNeeded() {
        if (currentBlock == null) {
            currentBlock = new SequencerBlock(blockCounter);
        }
    }

    /**
     * Gets the number of closed blocks, which is equal to the next block number to be assigned.
     *
     * @return the number of closed blocks
     */
    public int getClosedBlocksCount() {
        return closedBlocks.size();
    }

    /**
     * Closes the current block, publishes it to the closed-block map, and wakes waiters.
     */
    private void closeCurrentBlock() {
        closedBlocks.put(currentBlock.getBlockNumber(), currentBlock);
        blockCounter++;
        currentBlock = null;
        notifyAll();
    }

    /**
     * Closes the current block when its timeout has expired.
     */
    private void closeCurrentBlockIfTimedOut() {
        if (currentBlock != null
            && !currentBlock.isEmpty()
            && currentBlock.hasTimeout(LocalDateTime.now())) {

                closeCurrentBlock();
        }
    }


    /**
     * Closes the current block when it has reached maximum capacity.
     */
    private void closeCurrentBlockIfFull() {
        if (currentBlock != null && currentBlock.isFull()) {
            closeCurrentBlock();
        }
    }


    /**
     * Accepts a transaction with no dependencies.
     *
     * @param transaction the transaction to append
     * @return {@code true} if the transaction was accepted, {@code false} if it is a duplicate
     * @throws IllegalStateException if the sequencer is shutting down
     */
    public synchronized boolean addTransaction(SignedInternalTransaction transaction) {
        return addTransaction(transaction, List.of());
    }


    /**
     * Accepts a transaction into the current block (or buffers it if its dependencies
     * have not been sequenced yet) unless it is a duplicate or shutdown has started.
     *
     * <p>A transaction is considered a duplicate if its requestId has already been
     * sequenced or is already waiting in the buffer.
     *
     * <p>When a transaction is sequenced, any buffered transactions whose last
     * unresolved dependency was this transaction are automatically unblocked and
     * appended to the current block (cascading).
     *
     * @param transaction the transaction to append
     * @param dependsOn   requestIds that must be sequenced before this transaction
     * @return {@code true} if the transaction was accepted (sequenced or buffered),
     *         {@code false} if it is a duplicate
     * @throws IllegalStateException if the sequencer is shutting down
     */
    public synchronized boolean addTransaction(SignedInternalTransaction transaction, List<Long> dependsOn) {
        if (shuttingDown) {
            throw new IllegalStateException(ErrorMessage.SequencerShutdownMessage);
        }

        long requestId = transaction.getInternalTransaction().getRequestId();

        if (seenRequestIds.contains(requestId) || bufferedTransactions.containsKey(requestId)) {
            return false;
        }

        Set<Long> unresolved = new HashSet<>();
        for (Long dependency : dependsOn) {
            if (!seenRequestIds.contains(dependency)) {
                unresolved.add(dependency);
            }
        }

        if (unresolved.isEmpty()) {
            appendToBlock(transaction);
            processUnblockedTransactions(requestId);
        } else {
            BufferedTransaction buffered = new BufferedTransaction(transaction, unresolved);
            bufferedTransactions.put(requestId, buffered);
            for (Long dependency : unresolved) {
                dependencyWaiters.computeIfAbsent(dependency, k -> new ArrayList<>()).add(buffered);
            }
        }

        return true;
    }


    /**
     * Appends a dependency-resolved transaction to the current block, opening or
     * closing blocks as needed.
     */
    private void appendToBlock(SignedInternalTransaction transaction) {
        closeCurrentBlockIfTimedOut();
        openBlockIfNeeded();

        // If the block was empty we need to wake up any waiting thread
        boolean wasEmpty = currentBlock.isEmpty();

        currentBlock.addTransaction(sequenceCounter, transaction);
        sequenceCounter++;

        seenRequestIds.add(transaction.getInternalTransaction().getRequestId());

        if (wasEmpty) {
            notifyAll();
        }

        closeCurrentBlockIfFull();
    }


    /**
     * After a transaction is sequenced, checks whether any buffered transactions
     * that were waiting on it are now fully resolved. Resolved transactions are
     * appended to the current block, which may in turn unblock further
     * transactions (cascading).
     *
     * @param initialResolvedRequestId the requestId that was just sequenced
     */
    private void processUnblockedTransactions(long initialResolvedRequestId) {
        Queue<Long> toProcess = new LinkedList<>();
        toProcess.add(initialResolvedRequestId);

        while (!toProcess.isEmpty()) {
            long resolvedId = toProcess.poll();
            List<BufferedTransaction> waiters = dependencyWaiters.remove(resolvedId);
            if (waiters == null) {
                continue;
            }

            for (BufferedTransaction buffered : waiters) {
                buffered.unresolvedDependencies.remove(resolvedId);
                if (buffered.unresolvedDependencies.isEmpty()) {
                    long unblockedId = buffered.transaction.getInternalTransaction().getRequestId();
                    bufferedTransactions.remove(unblockedId);
                    appendToBlock(buffered.transaction);
                    toProcess.add(unblockedId);
                }
            }
        }
    }


    /**
     * Waits until the requested block number is available among the closed blocks.
     *
     * @param blockNumber the block number to deliver
     * @return the requested closed block
     * @throws InterruptedException if waiting is interrupted
     * @throws IllegalStateException if the sequencer is shutting down
     */
    public synchronized SequencerBlock getBlock(int blockNumber) throws InterruptedException {
        while (!closedBlocks.containsKey(blockNumber)) {
            if (shuttingDown) {
                throw new IllegalStateException(ErrorMessage.SequencerShutdownMessage);
            }

            closeCurrentBlockIfTimedOut();

            if (closedBlocks.containsKey(blockNumber)) {
                break;
            }

            if (currentBlock != null && !currentBlock.isEmpty()) {
                wait(SequencerBlock.MAX_NUMBER_OF_SECONDS * 1000L);
            } else {
                wait();
            }
        }

        return closedBlocks.get(blockNumber);
    }

    /**
     * Checks if the sequencer is currently starting up, defined as having no
     * closed blocks and an empty or non-existent current block.
     *
     * @return {@code true} if the sequencer is starting up, {@code false} otherwise
     */
    public synchronized boolean isStartingUp() {
        return this.closedBlocks.isEmpty() && (this.currentBlock == null || this.currentBlock.isEmpty());
    }

    /**
     * Initiates sequencer shutdown, flushing any partial block and waking waiting threads.
     */
    public synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }

        shuttingDown = true;

        // Flush partial work so waiting nodes can still receive it.
        if (currentBlock != null && !currentBlock.isEmpty()) {
            closeCurrentBlock();
        }

        notifyAll();
    }
}