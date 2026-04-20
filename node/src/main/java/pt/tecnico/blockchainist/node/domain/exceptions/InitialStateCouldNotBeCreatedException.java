package pt.tecnico.blockchainist.node.domain.exceptions;

import java.io.Serial;

/**
 * Thrown when the node cannot create or bootstrap its initial local state.
 */
public class InitialStateCouldNotBeCreatedException extends Exception{

    @Serial
    private static final long serialVersionUID = 202603011125L;

    /**
     * Creates a new exception with the given detail message.
     *
     * @param key the explanation of the failure
     */
    public InitialStateCouldNotBeCreatedException(String key){
        super(key);
    }
}
