package pt.tecnico.blockchainist.node.domain.block;

import java.util.Collections;
import java.util.List;

import pt.tecnico.blockchainist.common.transaction.InternalTransaction;
import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;

/**
 * Immutable node-side representation of a sequenced block received from the sequencer.
 */
public class NodeBlock {
    private final int blockNumber;
    private final List<SignedInternalTransaction> signedTransactions;

    /**
     * Creates a new node block.
     *
     * @param blockNumber the globally ordered block number
     * @param transactions the ordered transactions contained in the block
     */
    public NodeBlock(int blockNumber, List<SignedInternalTransaction> signedtTransactions) {
        this.blockNumber = blockNumber;
        this.signedTransactions = List.copyOf(signedtTransactions);
    }

    /**
     * Returns the block number.
     *
     * @return the globally ordered block number
     */
    public int getBlockNumber() {
        return blockNumber;
    }

    public List<SignedInternalTransaction> getSignedTransactions() {
        return Collections.unmodifiableList(signedTransactions);
    }

    public List<InternalTransaction> getTransactions() {
        return signedTransactions.stream()
            .map(SignedInternalTransaction::getInternalTransaction)
            .toList();
    }
}