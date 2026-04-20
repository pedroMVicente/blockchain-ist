package pt.tecnico.blockchainist.client.domain.message;

/*
 * This interface defines all help and usage messages for the client application.
 * It also includes generic error messages related to argument parsing and validation.
 */
public interface HelpMessage {

    String HELP_MESSAGE =
        "Usage:\n" +
        "- C|c <user_id> <wallet_id> <node_index> <node_delay>\n" +
        "- E|e <user_id> <wallet_id> <node_index> <node_delay>\n" +
        "- S <wallet_id> <node_index> <node_delay>\n" +
        "- T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>\n" +
        "- B <node_index>\n" +
        "- P <integer>\n" +
        "- X\n";
}