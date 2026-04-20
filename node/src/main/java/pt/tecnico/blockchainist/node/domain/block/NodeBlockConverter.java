package pt.tecnico.blockchainist.node.domain.block;

import java.util.List;
import java.util.stream.IntStream;

import pt.tecnico.blockchainist.common.transaction.SignedInternalTransaction;
import pt.tecnico.blockchainist.common.transaction.TransactionConverter;
import pt.tecnico.blockchainist.contract.Block;

/**
 * Converts protobuf blocks received from the sequencer into node-side block objects.
 */
public class NodeBlockConverter {

    private NodeBlockConverter() {}


    /**
     * Converts a protobuf {@link pt.tecnico.blockchainist.contract.Block} to a {@link NodeBlock}.
     *
     * @param block the protobuf block received from the sequencer
     * @return the converted node block
     */
    public static NodeBlock convertToNodeBlock(Block block) {
        List<SignedInternalTransaction> transactions = IntStream
            .range(0, block.getTransactionsCount())
            .mapToObj(block::getTransactions)
            .map(TransactionConverter::convertToSignedInternalTransaction)
            .toList();

        return new NodeBlock(block.getBlockNumber(), transactions);
    }
}