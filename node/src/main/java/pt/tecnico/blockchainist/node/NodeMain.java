package pt.tecnico.blockchainist.node;

import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.node.domain.NodeState;
import pt.tecnico.blockchainist.node.domain.OrganizationUsers;
import pt.tecnico.blockchainist.node.grpc.NodeInterceptor;
import pt.tecnico.blockchainist.node.grpc.NodeServiceImpl;
import pt.tecnico.blockchainist.node.domain.exceptions.InitialStateCouldNotBeCreatedException;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;


/**
 * Entry point for a blockchain node process.
 *
 * <p>Parses command-line arguments, initializes the node state, starts
 * the gRPC server, and blocks until the server terminates.
 *
 * <p>Usage: {@code mvn exec:java -Dexec.args="<port> <organization> <seq_host>:<seq_port>"}
 */
public class NodeMain {

    private static final String CLASSNAME = NodeMain.class.getSimpleName();
    private static final int MAX_PORT_NUMBER = 65535;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "node-shutdown-hook";

    /**
    * Parses startup arguments, initializes node state and background services,
    * starts the gRPC server, and blocks until shutdown.
    *
    * @param args expects {@code <port> <organization> <seq_host>:<seq_port>}
    */
    public static void main(String[] args) {

        System.out.println(CLASSNAME);

        // check arguments
        if (args.length < 3) {
            System.err.println("Argument(s) missing!");
            printUsage();
            return;
        }

        int port = -1;
        String organization = null;
        String seqHost = null;
        int seqPort = -1;

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port (" + args[0] + ") in argument: " + args);
            printUsage();
            return;
        }
        if (port > MAX_PORT_NUMBER || port < 0) {
            System.err.println("Port number out of range (0-" + MAX_PORT_NUMBER + "): " + port);
            printUsage();
            return;
        }
        organization = args[1];
        String[] split = args[2].split(":");

        if (split.length != 2) {
            System.err.println("Invalid argument: " + args[2]);
            printUsage();
            return;
        }

        seqHost = split[0];

        try {
            seqPort = Integer.parseInt(split[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port (" + split[1] + ") in argument: " + args[2]);
            printUsage();
            return;
        }
        if (seqPort > MAX_PORT_NUMBER || seqPort < 0) {
            System.err.println("Port number out of range (0-" + MAX_PORT_NUMBER + "): " + seqPort);
            printUsage();
            return;
        }

        NodeState nodeState;
        try {
            OrganizationUsers.init(organization);
            nodeState = new NodeState(organization);
        } catch (InitialStateCouldNotBeCreatedException e) {
            System.err.println("Error initializing node state: " + e.getMessage());
            return;
        }

        NodeServiceImpl nodeServiceImpl = new NodeServiceImpl(
            nodeState,
            seqHost,
            seqPort
        );

        try {
            Server server = ServerBuilder
                .forPort(port)
                .addService(ServerInterceptors.intercept(nodeServiceImpl, new NodeInterceptor()))
                .build();

            server.start();
            DebugLog.log(CLASSNAME, "========================================");
            DebugLog.log(CLASSNAME, "Node port    - " + port);
            DebugLog.log(CLASSNAME, "Organization - " + organization);
            DebugLog.log(CLASSNAME, "Sequencer    - " + seqHost + ":" + seqPort);
            DebugLog.log(CLASSNAME, "========================================");

            // Shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DebugLog.log(CLASSNAME, "Shutting down node...");
                try {
                    nodeServiceImpl.shutdown();
                    DebugLog.log(CLASSNAME, "ClientSequencerService connection closed");
                } catch (InterruptedException e) {
                    DebugLog.log(CLASSNAME, "Shutdown interrupted: " + e.getMessage());
                }
                server.shutdown();
                try {
                    if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                        DebugLog.log(CLASSNAME, "Server timeout, forcing shutdown");
                        server.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    DebugLog.log(CLASSNAME, "Shutdown interrupted, forcing");
                    server.shutdownNow();
                }
                DebugLog.log(CLASSNAME, "Server shutdown complete");
            }, SHUTDOWN_HOOK_THREAD_NAME));

            // Keep server running
            server.awaitTermination();
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            // Fatal error: server failed to start (e.g., port already in use)
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Server interrupted: " + e.getMessage());
        }
    }

    /**
    * Prints the expected command-line syntax for starting a node process.
    */
    private static void printUsage() {
        System.err.println("Usage: mvn exec:java -Dexec.args=\"<port> <organization> <seq_host>:<seq_port>\"");
        System.err.println("Optional: Add -Ddebug flag to enable debug mode");
    }
}
