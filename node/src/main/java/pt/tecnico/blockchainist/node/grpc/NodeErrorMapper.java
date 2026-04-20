package pt.tecnico.blockchainist.node.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.blockchainist.node.domain.exceptions.BalanceIsNotZeroException;
import pt.tecnico.blockchainist.node.domain.exceptions.DestinationWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.IncorrectOwnerException;
import pt.tecnico.blockchainist.node.domain.exceptions.NonPositiveTransferAmountException;
import pt.tecnico.blockchainist.node.domain.exceptions.SourceWalletDoesNotExistException;
import pt.tecnico.blockchainist.node.domain.exceptions.SparseBalanceFromOriginException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletAlreadyExistsException;
import pt.tecnico.blockchainist.node.domain.exceptions.WalletDoesNotExistException;

/**
 * Maps domain-layer exceptions to the gRPC status that should be returned to clients.
 */
public class NodeErrorMapper {

    /**
     * Converts a domain exception into its gRPC transport representation.
     *
     * @param e the domain exception raised while applying a transaction
     * @return the gRPC error to return to the caller
     */
    public StatusRuntimeException mapDomainException(Exception e) {
        if (e instanceof WalletAlreadyExistsException) {
            return Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof WalletDoesNotExistException
            || e instanceof SourceWalletDoesNotExistException
            || e instanceof DestinationWalletDoesNotExistException) {
            return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof IncorrectOwnerException) {
            return Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof BalanceIsNotZeroException
            || e instanceof SparseBalanceFromOriginException) {
            return Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof NonPositiveTransferAmountException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
        }

        return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
    }
}