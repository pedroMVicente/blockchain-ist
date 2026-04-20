package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when trying to create a wallet whose identifier already exists.
 */
public class WalletAlreadyExistsException  extends Exception{
    
    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public WalletAlreadyExistsException(String key){
        super(key);
    }
}
