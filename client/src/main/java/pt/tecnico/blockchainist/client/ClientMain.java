package pt.tecnico.blockchainist.client;

import java.util.ArrayList;

import pt.tecnico.blockchainist.client.domain.CommandProcessor;
import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.common.DebugLog;

/**
 * Entry point for the blockchain client process.
 *
 * <p>Parses command-line arguments to build a list of node connections,
 * then enters an interactive command loop that forwards user operations
 * to the configured nodes.
 *
 * <p>Usage: {@code mvn exec:java -Dexec.args="<host>:<port>:<organization> [<host>:<port>:<organization> ...]"}
 */
public class ClientMain {

    private static final int MAX_PORT_NUMBER = 65535;
    private static final String CLASSNAME = ClientMain.class.getSimpleName();

    /**
     * Starts the client process.
     *
     * <p>Each argument must be in {@code host:port:organization} form.
     * A {@link ClientNodeService} is created for every valid argument.
     * After the command loop exits, all node connections are shut down.
     *
     * @param args command-line arguments: one or more {@code <host>:<port>:<organization>} entries
     */
    public static void main(String[] args) {

        System.out.println(CLASSNAME);

        // check arguments
        if (args.length < 1) {
            System.err.println("Argument(s) missing!");
            printUsage();
            return;
        }

        // parse arguments
        ArrayList<ClientNodeService> nodes = new ArrayList<>(args.length);
        for (String arg : args) {
            String[] split = arg.split(":");
            if (split.length != 3) {
                System.err.println("Invalid argument: " + arg);
                printUsage();
                return;
            }
            String host = split[0];
            int port = -1;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port (" + split[1] + ") in argument: " + arg);
                printUsage();
                return;
            }
            if (port > MAX_PORT_NUMBER || port < 0) {
                System.err.println("Port number out of range (0-" + MAX_PORT_NUMBER + "): " + port);
                printUsage();
                return;
            }
            String organization = split[2];
            DebugLog.log(CLASSNAME, "connecting to node host=" + host + " port=" + port + " organization=" + organization);
            nodes.add(new ClientNodeService(host, port, organization));
        }
        DebugLog.log(CLASSNAME, "========================================");
        DebugLog.log(CLASSNAME, "Client initialized with " + nodes.size() + " node(s)");
        DebugLog.log(CLASSNAME, "========================================");

        CommandProcessor processor = new CommandProcessor(nodes);
        processor.userInputLoop();

        for (ClientNodeService node : nodes) {
            try {
                node.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Prints usage instructions to standard error.
     */
    private static void printUsage() {
        System.err.println("Usage: mvn exec:java -Dexec.args=\"<host>:<port>:<organization> [<host>:<port>:<organization> ...]\"");
    }
}
