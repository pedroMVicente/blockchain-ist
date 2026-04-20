package pt.tecnico.blockchainist.sequencer.domain.block;

import java.util.Map;

import pt.tecnico.blockchainist.common.transaction.TransactionConverter;
import pt.tecnico.blockchainist.contract.Block;

/**
 * Converts sequencer-side blocks to the protobuf representation sent to nodes.
 */
public class SequencerBlockConverter {

    private SequencerBlockConverter() {}

    /**
     * Converts a {@link SequencerBlock} into a protobuf {@link Block} message.
     *
     * @param block the sequencer block to serialize
     * @return the protobuf block message
     */
    public static Block convertToBlock(SequencerBlock block) {
        Block.Builder builder = Block.newBuilder()
            .setBlockNumber(block.getBlockNumber());

        block.getTransactions().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .map(TransactionConverter::convertToSignedTransaction)
            .forEach(builder::addTransactions);

        return builder.build();
    }
}