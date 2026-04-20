package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when a transfer amount is zero or negative.
 */
public class NonPositiveTransferAmountException extends Exception{
    @Serial
    private static final long serialVersionUID = 202603011125L;


    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public NonPositiveTransferAmountException(String key){
        super(key);
    }
}
