package pt.tecnico.blockchainist.sequencer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.sequencer.domain.SequencerState;
import pt.tecnico.blockchainist.sequencer.grpc.SequencerServiceImpl;

/**
 * Entry point for the central sequencer process.
 *
 * <p>The sequencer collects transactions broadcast by nodes, groups them into
 * globally ordered blocks, and serves those closed blocks back to nodes through gRPC.
 *
 * <p>Usage:
 * {@code mvn exec:java -Dexec.args="<port> <blockSize> <blockTimeout>"}.
 */
public class SequencerMain {
	
	private static int blockTimeout = 5;
    private static int blockSize = 4;
	private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
	private static final int MAX_PORT_NUMBER = 65535;
	    private static final String SHUTDOWN_HOOK_THREAD_NAME = "sequencer-shutdown-hook";
	private static final String CLASSNAME = SequencerMain.class.getSimpleName();

	/**
	 * Parses startup arguments, validates the sequencer configuration, starts the
	 * gRPC server, and blocks until shutdown.
	 *
	 * @param args expects {@code <port> <blockSize> <blockTimeout>}
	 */
	public static void main(String[] args) {

		System.out.println(CLASSNAME);

		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			printUsage();
			return;
		}

		int port = -1;
		try {
			port = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			System.err.println("Invalid port (" + args[0] + ") in argument: " + args);
			printUsage();
			return;
		}

		// Validate that the port is within the legal TCP range.
		if (port > MAX_PORT_NUMBER || port < 0) {
			System.err.println("Port number out of range (0-" + MAX_PORT_NUMBER + "): " + port);
			printUsage();
			return;
		}

		if(args.length >= 2){
			try {
				blockSize = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid blockSize (" + args[1] + ") in argument: " + args);
				printUsage();
				return;
			}

			if (blockSize <= 0) {
				System.err.println("Non-positive block size value: " + blockSize);
				printUsage();
				return;
			}
		}

		if(args.length >= 3){
			try {
				blockTimeout = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid blockTimeout (" + args[2] + ") in argument: " + args);
				printUsage();
				return;
			}

			if (blockTimeout <= 0) {
				System.err.println("Non-positive block timeout value: " + blockTimeout);
				printUsage();
				return;
			}
		}

        SequencerServiceImpl sequencerServiceImpl = new SequencerServiceImpl(new SequencerState());
		Server server = ServerBuilder
			.forPort(port)
			.addService(sequencerServiceImpl)
			.build();

		try {
			server.start();
			DebugLog.log(CLASSNAME, "========================================");
			DebugLog.log(CLASSNAME, "Sequencer port - " + port);
			DebugLog.log(CLASSNAME, "========================================");


            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DebugLog.log(CLASSNAME, "Shutting down sequencer...");

                sequencerServiceImpl.shutdown();

                server.shutdown();
                try {
                    if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        DebugLog.log(CLASSNAME, "Server timeout, forcing shutdown");
                        server.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    DebugLog.log(CLASSNAME, "Shutdown interrupted, forcing");
                    server.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                DebugLog.log(CLASSNAME, "Server shutdown complete");
            }, SHUTDOWN_HOOK_THREAD_NAME));


			server.awaitTermination();

		} catch (IOException e) {
			DebugLog.log(CLASSNAME, "Failed to start sequencer server: " + e.getMessage());
			System.exit(1);

		} catch (InterruptedException e) {
			DebugLog.log(CLASSNAME, "Sequencer main thread interrupted while waiting for termination");
			Thread.currentThread().interrupt();
		}
	}


	/**
	 * Prints correct usage instructions to standard error.
	 */
	private static void printUsage() {
		System.err.println("Usage: mvn exec:java -Dexec.args=\"<port> <blockSize> <blockTimeout>\"");
	}


	/**
	 * Returns the configured maximum number of transactions per block.
	 *
	 * @return the block size configured at startup
	 */
	public static int getBlockSize(){
		return blockSize;
	}

	/**
	 * Returns the configured block timeout in seconds.
	 *
	 * @return the block timeout configured at startup
	 */
	public static int getBlockTimeout(){
		return blockTimeout;
	}
}
