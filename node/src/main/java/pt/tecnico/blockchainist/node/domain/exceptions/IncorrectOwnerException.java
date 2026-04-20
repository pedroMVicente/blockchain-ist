package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when a user attempts to operate on a wallet they do not own.
 */
public class IncorrectOwnerException extends Exception{

    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public IncorrectOwnerException(String key){
        super(key);
    }
}
