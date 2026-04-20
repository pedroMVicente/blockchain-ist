package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when the source wallet does not have enough balance to complete a transfer.
 */
public class SparseBalanceFromOriginException extends Exception{
    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public SparseBalanceFromOriginException(String key){
        super(key);
    }
}