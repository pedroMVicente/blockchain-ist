package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when a transfer references a source wallet that does not exist.
 */
public class SourceWalletDoesNotExistException extends Exception {
    
    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public SourceWalletDoesNotExistException(String key){
        super(key);
    }
}
