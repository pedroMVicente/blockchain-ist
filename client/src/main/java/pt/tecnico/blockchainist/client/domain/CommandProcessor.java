package pt.tecnico.blockchainist.client.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import static io.grpc.Status.Code.*;


import pt.tecnico.blockchainist.client.domain.exceptions.ClientNodeServiceException;
import pt.tecnico.blockchainist.client.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.client.domain.message.HelpMessage;
import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.client.grpc.NodeOperationDispatcher;
import pt.tecnico.blockchainist.client.grpc.AsyncUtils;
import pt.tecnico.blockchainist.common.transaction.InternalTransaction;

/**
 * Reads commands from standard input and dispatches them to the appropriate node.
 * Each numbered command prints a {@code [N] OK} success line or an error on failure.
 */
public class CommandProcessor {


    private static final String SPACE = " ";
    private static final String CREATE_BLOCKING = "C";
    private static final String CREATE_ASYNC = "c";
    private static final String DELETE_BLOCKING = "E";
    private static final String DELETE_ASYNC = "e";
    private static final String BALANCE_BLOCKING = "S";
    private static final String TRANSFER_BLOCKING = "T";
    private static final String TRANSFER_ASYNC = "t";
    private static final String DEBUG_BLOCKCHAIN_STATE = "B";
    private static final String PAUSE = "P";
    private static final String EXIT = "X";

    private static final String SUCCESSFUL_TRANSACTION_RESPONSE = "OK";
    private static final String BEGINNING_OF_INPUT_PROCESSING = "\n> ";

    private static final int MILLIS_PER_SECOND = 1000;

    private final AtomicLong commandCounter = new AtomicLong(0);
    private final int numNodes;
    private final NodeOperationDispatcher dispatcher;

    /**
     * Constructs a new CommandProcessor backed by the given list of nodes.
     *
     * @param nodes the list of node stubs the client may direct commands to
     */
    public CommandProcessor(ArrayList<ClientNodeService> nodes) {
        this.numNodes = nodes.size();
        this.dispatcher = new NodeOperationDispatcher(nodes);

    }

    /**
     * Continuously reads and dispatches commands from standard input until the exit command is given.
     */
    public void userInputLoop() {

        Scanner scanner = new Scanner(System.in);
        boolean exit = false;
        while (!exit) {
            System.out.print(BEGINNING_OF_INPUT_PROCESSING);
            String line = scanner.nextLine().trim();
            String[] split = line.split(SPACE);
            try {
                switch (split[0]) {
                    case CREATE_BLOCKING:
                        this.incrementCommandCounter();
                        this.create(split, true);
                        break;

                    case CREATE_ASYNC:
                        this.incrementCommandCounter();
                        this.create(split, false);
                        break;

                    case DELETE_BLOCKING:
                        this.incrementCommandCounter();
                        this.delete(split, true);
                        break;

                    case DELETE_ASYNC:
                        this.incrementCommandCounter();
                        this.delete(split, false);
                        break;

                    case BALANCE_BLOCKING:
                        this.incrementCommandCounter();
                        this.balance(split);
                        break;

                    case TRANSFER_BLOCKING:
                        this.incrementCommandCounter();
                        this.transfer(split, true);
                        break;

                    case TRANSFER_ASYNC:
                        this.incrementCommandCounter();
                        this.transfer(split, false);
                        break;

                    case DEBUG_BLOCKCHAIN_STATE:
                        this.incrementCommandCounter();
                        this.debugBlockchainState(split);
                        break;

                    case PAUSE:
                        this.pause(split);
                        break;

                    case EXIT:
                        exit = true;
                        break;

                    default:
                        printUsage();
                        break;
                }
            } catch (IllegalArgumentException e) {
                long commandNumber = this.getCommandCounter();
                System.err.println(
                    ErrorMessage.nodeError(commandNumber, e.getMessage())
                );
                printUsage();
            } catch (ClientNodeServiceException e) {
                long commandNumber = this.getCommandCounter();
                if (e.getStatusCode() == UNAVAILABLE) {
                    System.err.println(
                        ErrorMessage.nodeUnavailable(commandNumber)
                    );
                } else {
                    System.err.println(
                        ErrorMessage.nodeError(commandNumber, e.getMessage())
                    );
                }
            } catch (RuntimeException e) {
                // fallback for unexpected errors
                long commandNumber = this.getCommandCounter();
                System.err.println(
                    ErrorMessage.nodeError(commandNumber, e.getMessage())
                );
            }
        }
        scanner.close();
    }

    /**
     * Handles the create-wallet command.
     * <p>Usage: {@code C|c <user_id> <wallet_id> <node_index> <node_delay>}</p>
     *
     * @param split      the tokenised input line
     * @param isBlocking {@code true} for the blocking variant, {@code false} for async
     * @throws IllegalArgumentException   if the arguments are invalid
     * @throws ClientNodeServiceException if the node reports an error for this operation
     */
    private void create(String[] split, boolean isBlocking) {
        CheckCommands.checkCreateCommandArgs(split, numNodes);
        
        long commandNumber = this.getCommandCounter();
        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);

        if (isBlocking) {
            dispatcher.createWalletBlocking(userId, walletId, nodeIndex, nodeDelay);
            printSuccess(commandNumber, null);
        } else {
            CompletableFuture<Void> future = dispatcher.createWalletAsync(userId, walletId, nodeIndex, nodeDelay);
            printWhenAsyncDone(future, commandNumber);
        }
    }

    /**
     * Handles the delete-wallet command.
     * <p>Usage: {@code E|e <user_id> <wallet_id> <node_index> <node_delay>}</p>
     *
     * @param split      the tokenised input line
     * @param isBlocking {@code true} for the blocking variant, {@code false} for async
     * @throws IllegalArgumentException   if the arguments are invalid
     * @throws ClientNodeServiceException if the node reports an error for this operation
     */
    private void delete(
        String[] split,
        boolean isBlocking
    ) {
        CheckCommands.checkDeleteCommandArgs(split, numNodes);

        long commandNumber = this.getCommandCounter();
        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);

        if (isBlocking) {
            dispatcher.deleteWalletBlocking(userId, walletId, nodeIndex, nodeDelay);
            printSuccess(commandNumber, null);
        } else {
            CompletableFuture<Void> future = dispatcher.deleteWalletAsync(userId, walletId, nodeIndex, nodeDelay);
            printWhenAsyncDone(future, commandNumber);
        }
    }

    /**
     * Handles the read-balance command.
     * <p>Usage: {@code S|s <wallet_id> <node_index> <node_delay>}</p>
     *
     * @param split      the tokenised input line
     * @param isBlocking {@code true} for the blocking variant, {@code false} for async
     * @throws IllegalArgumentException   if the arguments are invalid
     * @throws ClientNodeServiceException if the node reports an error for this operation
     */
    private void balance(
        String[] split
    ) {
        CheckCommands.checkBalanceCommandArgs(split, numNodes);

        long commandNumber = this.getCommandCounter();
        String walletId = split[1];
        Integer nodeIndex = Integer.parseInt(split[2]);
        Integer nodeDelay = Integer.parseInt(split[3]);

        long balance = dispatcher.readBalance(walletId, nodeIndex, nodeDelay);
        printSuccess(commandNumber, String.valueOf(balance));
    }

    /**
     * Handles the transfer command.
     * <p>Usage: {@code T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>}</p>
     *
     * @param split      the tokenised input line
     * @param isBlocking {@code true} for the blocking variant, {@code false} for async
     * @throws IllegalArgumentException   if the arguments are invalid
     * @throws ClientNodeServiceException if the node reports an error for this operation
     */
    private void transfer(String[] split, boolean isBlocking) {
        CheckCommands.checkTransferCommandArgs(split, numNodes);

        long commandNumber = this.getCommandCounter();
        String sourceUserId = split[1];
        String sourceWalletId = split[2];
        String destinationWalletId = split[3];
        Long amount = Long.parseLong(split[4]);
        Integer nodeIndex = Integer.parseInt(split[5]);
        Integer nodeDelay = Integer.parseInt(split[6]);

        if (isBlocking) {
            dispatcher.transferBlocking(sourceUserId, sourceWalletId, destinationWalletId, amount, nodeIndex, nodeDelay);
            printSuccess(commandNumber, null);
        } else {
            CompletableFuture<Void> future = dispatcher.transferAsync(sourceUserId, sourceWalletId, destinationWalletId, amount, nodeIndex, nodeDelay);
            printWhenAsyncDone(future, commandNumber);
        }

    }

    /**
     * Prints all the transactions recorded in the given node.
     * <p>Usage: {@code B <node_index>}</p>
     *
     * @param split the tokenised input line
     * @throws IllegalArgumentException   if the arguments are invalid
     * @throws ClientNodeServiceException if the node reports an error for this operation
     */
    private void debugBlockchainState(String[] split) {
        CheckCommands.checkDebugBlockchainStateArgs(split, numNodes);

        long commandNumber = this.getCommandCounter();
        Integer nodeIndex = Integer.parseInt(split[1]);

        List<InternalTransaction> transactions = dispatcher.getBlockchainState(nodeIndex);
        printSuccess(commandNumber, null);
        for (InternalTransaction transaction : transactions) {
            System.out.println(transaction);
        }
    }

    /**
     * Handles the pause command, blocking the thread for the given number of seconds.
     * <p>Usage: {@code P <integer>}</p>
     *
     * @param split the tokenised input line
     * @throws IllegalArgumentException if the arguments are invalid
     */
    private void pause(String[] split) {
        CheckCommands.checkPauseArgs(split);

        Integer time;

        time = Integer.parseInt(split[1]);

        try {
            Thread.sleep(time * MILLIS_PER_SECOND);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    /** Increments the command counter and returns the new value. */
    private void incrementCommandCounter() {
        this.commandCounter.incrementAndGet();
    }

    /** Returns the current command counter value. */
    private long getCommandCounter() {
        return this.commandCounter.get();
    }

    /**
     * Prints a success message for the current command in the form {@code OK <commandNumber>},
     * followed by the node's response on the next line if there is one.
     *
     * @param response the node's response, or {@code null} if the command produces no output
     */
    private void printSuccess(long commandNumber, String response) {
        System.out.println(SUCCESSFUL_TRANSACTION_RESPONSE + " " + commandNumber);
        if (response != null) {
            System.out.println(response);
        }
    }

    private void printWhenAsyncDone(CompletableFuture<Void> future, long commandNumber) {
        future.whenComplete((result, error) -> {
            if (error == null) {
                printSuccess(commandNumber, null);
                return;
            }
            
            Throwable cause = AsyncUtils.unwrapAsyncThrowable(error);
            ClientNodeServiceException e = (ClientNodeServiceException) cause;
            if (e.getStatusCode() == UNAVAILABLE) {
                System.err.println(ErrorMessage.nodeUnavailable(commandNumber));
            } else {
                System.err.println(ErrorMessage.nodeError(commandNumber, e.getMessage()));
            }
        });
    }

    /** Prints command usage information to standard error. */
    private static void printUsage() {
        System.err.println(HelpMessage.HELP_MESSAGE);
    }
}
