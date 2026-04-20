package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when an operation references a wallet that does not exist.
 */
public class WalletDoesNotExistException extends Exception {
    
    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public WalletDoesNotExistException(String key){
        super(key);
    }
}
