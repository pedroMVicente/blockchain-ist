package pt.tecnico.blockchainist.client.domain.exceptions;

import java.io.Serial;
import io.grpc.Status;

/**
 * Thrown when a client RPC call to a node fails and must carry the corresponding gRPC status code.
 */
public class ClientNodeServiceException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 202603031401L;

    private final Status.Code statusCode;

    /**
     * Creates a new exception with the given detail message and gRPC status code.
     *
     * @param message the explanation of the failure
     * @param statusCode the gRPC status code associated with the failure
     */
    public ClientNodeServiceException(String message, Status.Code statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Returns the gRPC status code associated with this failure.
     *
     * @return the gRPC status code
     */
    public Status.Code getStatusCode() { return statusCode; }
}