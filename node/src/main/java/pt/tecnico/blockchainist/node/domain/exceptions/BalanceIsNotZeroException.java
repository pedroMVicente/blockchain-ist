package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when an operation requires a wallet to have zero balance, but the wallet still contains funds.
 */
public class BalanceIsNotZeroException extends Exception {
    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public BalanceIsNotZeroException(String key) {
        super(key);
    }
}
