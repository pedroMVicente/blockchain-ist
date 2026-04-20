package pt.tecnico.blockchainist.sequencer.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when a block violates the sequencer's expected structure or ordering rules.
 */
public class InvalidBlockException extends Exception {
    @Serial
    private static final long serialVersionUID = 202603042009L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public InvalidBlockException(String key) {
        super(key);
    }
}
