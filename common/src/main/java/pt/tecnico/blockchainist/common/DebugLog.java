package pt.tecnico.blockchainist.common;

/**
 * Centralized debug logging utility.
 *
 * Emits structured debug messages to stderr when the {@code -Ddebug} flag is set.
 * This allows tracing request flow across distributed components (client, node, sequencer)
 * without cluttering production output or requiring a full logging framework.
 *
 * Usage:
 *   DebugLog.log("NodeService", "createWallet received walletId=abc123");
 *
 * Enable debug output:
 *   mvn exec:java -Ddebug -Dexec.args="..."
 */
public final class DebugLog {

	private static final boolean DEBUG_ENABLED = System.getProperty("debug") != null;

	private DebugLog() {}

	/**
	 * Emits a debug log line to stderr if debug mode is enabled.
	 *
	 * @param classname the name of the class emitting the log (e.g., "NodeService", "SequencerService")
	 * @param message   the log message with contextual details (include operation name and identifiers)
	 */
	public static void log(String classname, String message) {
		if (DEBUG_ENABLED) {
			System.err.println("[DEBUG][" + classname + "] " + message);
		}
	}
}
