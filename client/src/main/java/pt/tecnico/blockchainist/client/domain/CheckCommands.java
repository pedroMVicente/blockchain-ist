package pt.tecnico.blockchainist.client.domain;

import java.util.regex.Pattern;

import pt.tecnico.blockchainist.client.domain.message.ErrorMessage;

final class CheckCommands {

    private static final int NUMBER_OF_ARGS_CREATE = 5;
    private static final int NUMBER_OF_ARGS_DELETE = 5;
    private static final int NUMBER_OF_ARGS_BALANCE = 4;
    private static final int NUMBER_OF_ARGS_TRANSFER = 7;
    private static final int NUMBER_OF_ARGS_BLOCKCHAIN = 2;
    private static final int NUMBER_OF_ARGS_PAUSE = 2;

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CheckCommands() {
    }

    /**
     * Validates arguments for the create-wallet command ({@code C|c <user_id> <wallet_id> <node_index> <node_delay>}).
     *
     * @param split the tokenised input line
     * @param nodesSize the number of nodes configured in the client
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkCreateCommandArgs(
        String[] split,
        int nodesSize
    ) {
        // C|c <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != NUMBER_OF_ARGS_CREATE) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_CREATE,
                    split.length
                )
            );
        }

        String userId = split[1];

        if (!ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidUserId(userId)
            );
        }

        String walletId = split[2];

        if (!ID_PATTERN.matcher(walletId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidWalletId(walletId)
            );
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= nodesSize) {
                throw new IllegalArgumentException(
                    ErrorMessage.nodeIndexOutOfRange(nodesSize - 1)
                );
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.NODE_DELAY_NEGATIVE
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.CREATE_NUMERIC_ARGS_INVALID
            );
        }
    }

    /**
     * Validates arguments for the delete-wallet command ({@code E|e <user_id> <wallet_id> <node_index> <node_delay>}).
     *
     * @param split the tokenised input line
     * @param nodesSize the number of nodes configured in the client
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkDeleteCommandArgs(
        String[] split,
        int nodesSize
    ) {
        // E|e <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != NUMBER_OF_ARGS_DELETE) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_DELETE,
                    split.length
                )
            );
        }

        String userId = split[1];

        if (!ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidUserId(userId)
            );
        }

        String walletId = split[2];

        if (!ID_PATTERN.matcher(walletId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidWalletId(walletId)
            );
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= nodesSize) {
                throw new IllegalArgumentException(
                    ErrorMessage.nodeIndexOutOfRange(nodesSize - 1)
                );
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.NODE_DELAY_NEGATIVE
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.DELETE_NUMERIC_ARGS_INVALID
            );
        }
    }

    /**
     * Validates arguments for the read-balance command ({@code S|s <wallet_id> <node_index> <node_delay>}).
     *
     * @param split the tokenised input line
     * @param nodesSize the number of nodes configured in the client
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkBalanceCommandArgs(
        String[] split,
        int nodesSize
    ) {
        // S|s <wallet_id> <node_index> <node_delay>
        if (split.length != NUMBER_OF_ARGS_BALANCE) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_BALANCE,
                    split.length
                )
            );
        }

        String walletId = split[1];
        if (!ID_PATTERN.matcher(walletId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidWalletId(walletId)
            );
        }

        try {
            int nodeIndex = Integer.parseInt(split[2]);
            if (nodeIndex < 0 || nodeIndex >= nodesSize) {
                throw new IllegalArgumentException(
                    ErrorMessage.nodeIndexOutOfRange(nodesSize - 1)
                );
            }
            if (Integer.parseInt(split[3]) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.NODE_DELAY_NEGATIVE
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.BALANCE_NUMERIC_ARGS_INVALID
            );
        }
    }

    /**
     * Validates arguments for the transfer command
     * ({@code T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>}).
     *
     * @param split the tokenised input line
     * @param nodesSize the number of nodes configured in the client
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkTransferCommandArgs(
        String[] split, 
        int nodesSize
    ) {
        // T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>
        if (split.length != NUMBER_OF_ARGS_TRANSFER) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_TRANSFER,
                    split.length
                )
            );
        }

        String userId = split[1];

        if (!ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidSourceUserId(userId)
            );
        }

        String srcWalletId = split[2];

        if (!ID_PATTERN.matcher(srcWalletId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidSourceWalletId(srcWalletId)
            );
        }

        String destWalletId = split[3];

        if (!ID_PATTERN.matcher(destWalletId).matches()) {
            throw new IllegalArgumentException(
                ErrorMessage.invalidDestinationWalletId(destWalletId)
            );
        }


        try {
            String ammount = split[4];

            if (Long.parseLong(ammount) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.AMOUNT_NEGATIVE
                );
            }

            int nodeIndex = Integer.parseInt(split[5]);
            if (nodeIndex < 0 || nodeIndex >= nodesSize) {
                throw new IllegalArgumentException(
                    ErrorMessage.nodeIndexOutOfRange(nodesSize - 1)
                );
            }
            if (Integer.parseInt(split[6]) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.NODE_DELAY_NEGATIVE
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.TRANSFER_NUMERIC_ARGS_INVALID
            );
        }
    }

    /**
     * Validates arguments for the blockchain-state command ({@code B <node_index>}).
     *
     * @param split the tokenised input line
     * @param nodesSize the number of nodes configured in the client
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkDebugBlockchainStateArgs(
        String[] split,
        int nodesSize
    ) {
        // B <node_index>
        if (split.length != NUMBER_OF_ARGS_BLOCKCHAIN) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_BLOCKCHAIN,
                    split.length
                )
            );
        }

        try {
            int nodeIndex = Integer.parseInt(split[1]);
            if (nodeIndex < 0 || nodeIndex >= nodesSize) {
                throw new IllegalArgumentException(
                    ErrorMessage.nodeIndexOutOfRange(
                        nodesSize - 1
                    )
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.DEBUG_NUMERIC_ARGS_INVALID
            );
        }
    }

    /**
     * Validates arguments for the pause command ({@code P <integer>}).
     *
     * @param split the tokenised input line
     * @throws IllegalArgumentException if any argument is missing or invalid
     */
    static void checkPauseArgs(String[] split) {
        // P <integer>
        if (split.length != NUMBER_OF_ARGS_PAUSE) {
            throw new IllegalArgumentException(
                ErrorMessage.wrongArgCount(
                    NUMBER_OF_ARGS_PAUSE,
                    split.length
                )
            );
        }

        try {
            if (Integer.parseInt(split[1]) < 0) {
                throw new IllegalArgumentException(
                    ErrorMessage.PAUSE_TIME_NEGATIVE
                );
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                ErrorMessage.PAUSE_NUMERIC_ARGS_INVALID
            );
        }
    }
}
