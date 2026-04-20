package pt.tecnico.blockchainist.sequencer.domain.block;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;
import pt.tecnico.blockchainist.sequencer.SequencerMain;
import pt.tecnico.blockchainist.sequencer.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.sequencer.domain.message.LogMessage;

/**
 * Mutable block under construction inside the sequencer.
 */
public class SequencerBlock {
    private static final String CLASSNAME = SequencerBlock.class.getSimpleName();
    public static final int MAX_NUMBER_OF_TRANSACTIONS = SequencerMain.getBlockSize();
    public static final long MAX_NUMBER_OF_SECONDS = SequencerMain.getBlockTimeout();

    private final LocalDateTime creationTime;
    private final int blockNumber;
    private final Map<Integer, SignedInternalTransaction> transactions;

    /**
     * Creates a new block with the configured size and timeout parameters.
     *
     * @param blockNumber the global block number assigned by the sequencer
     */
    public SequencerBlock(int blockNumber) {

        DebugLog.log(CLASSNAME, LogMessage.blockSize(blockNumber, MAX_NUMBER_OF_TRANSACTIONS));
        DebugLog.log(CLASSNAME, LogMessage.blockTimeout(blockNumber, MAX_NUMBER_OF_SECONDS));
        this.blockNumber = blockNumber;
        this.creationTime = LocalDateTime.now();
        this.transactions = new TreeMap<>();
    }


    /**
     * Returns the global block number.
     *
     * @return the block number
     */
    public int getBlockNumber() {
        return this.blockNumber;
    }


    /**
     * Adds a transaction to the block in global sequence order.
     *
     * @param sequenceNumber the globally ordered sequence number assigned to the transaction
     * @param transaction the transaction to append
     */
    public synchronized void addTransaction(int sequenceNumber, SignedInternalTransaction transaction) {
        if (isReadyToClose()) {
            throw new IllegalStateException(ErrorMessage.CannotAddTransactionToClosedBlockMessage);
        }
        this.transactions.put(sequenceNumber, transaction);
    }


    /**
     * Returns a read-only view of the transactions currently stored in the block.
     *
     * @return the block transactions keyed by sequence number
     */
    public Map<Integer, SignedInternalTransaction> getTransactions() {
        return Collections.unmodifiableMap(this.transactions);
    }


    /**
     * Indicates whether the block currently holds any transactions.
     *
     * @return {@code true} when the block is empty
     */
    public synchronized boolean isEmpty() {
        return this.transactions.isEmpty();
    }


    /**
     * Indicates whether the block reached the configured capacity.
     *
     * @return {@code true} when the block is full
     */
    public synchronized boolean isFull() {
        return this.transactions.size() >= MAX_NUMBER_OF_TRANSACTIONS;
    }


    /**
     * Checks whether the block timeout has expired relative to the given time.
     *
     * @param currentTime the reference time to compare against
     * @return {@code true} when the timeout has expired
     */
    public boolean hasTimeout(LocalDateTime currentTime) {
        long seconds = ChronoUnit.SECONDS.between(this.creationTime, currentTime);
        return seconds >= MAX_NUMBER_OF_SECONDS;
    }


/**
     * Indicates whether the block can now be closed because it is full or timed out.
     *
     * @return {@code true} when the block should be closed
     */
    public synchronized boolean isReadyToClose() {
        return !isEmpty() && (hasTimeout(LocalDateTime.now()) || isFull());
    }
}