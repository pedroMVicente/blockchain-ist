package pt.tecnico.blockchainist.node.grpc;

import java.util.regex.Pattern;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.TransferResponse;
import pt.tecnico.blockchainist.node.domain.OrganizationUsers;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;


/**
 * Validates incoming node gRPC requests before they enter transactional execution.
 */
public class NodeRequestValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    /**
     * Validates a create wallet request before it is turned into a transaction.
     *
     * @param request the incoming create-wallet request
     * @param responseObserver the observer used to report validation failures
     * @return {@code true} when the request is valid, {@code false} otherwise
     */
    public boolean validateCreateWalletRequest(
        CreateWalletRequest request,
        StreamObserver<CreateWalletResponse> responseObserver
    ) {
        if (!isValidId(request.getUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidUserIdMessage)
                .asRuntimeException());
            return false;
        }

        if (!userBelongsToNodeOrganization(request.getUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.UserDoesNotBelongToOrganizationMessage)
                .asRuntimeException());
            return false;
        }

        if (!isValidId(request.getWalletId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidWalletIdMessage)
                .asRuntimeException());
            return false;
        }

        return true;
    }


    /**
     * Validates a delete wallet request before it is turned into a transaction.
     *
     * @param request the incoming delete-wallet request
     * @param responseObserver the observer used to report validation failures
     * @return {@code true} when the request is valid, {@code false} otherwise
     */
    public boolean validateDeleteWalletRequest(
        DeleteWalletRequest request,
        StreamObserver<DeleteWalletResponse> responseObserver
    ) {
        if (!isValidId(request.getUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidUserIdMessage)
                .asRuntimeException());
            return false;
        }

        if (!userBelongsToNodeOrganization(request.getUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.UserDoesNotBelongToOrganizationMessage)
                .asRuntimeException());
            return false;
        }

        if (!isValidId(request.getWalletId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidWalletIdMessage)
                .asRuntimeException());
            return false;
        }

        return true;
    }


    /**
     * Validates a read balance request before it is executed locally.
     *
     * @param request the incoming read-balance request
     * @param responseObserver the observer used to report validation failures
     * @return {@code true} when the request is valid, {@code false} otherwise
     */
    public boolean validateReadBalanceRequest(
        ReadBalanceRequest request,
        StreamObserver<ReadBalanceResponse> responseObserver
    ) {
        if (!isValidId(request.getWalletId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidWalletIdMessage)
                .asRuntimeException());
            return false;
        }

        return true;
    }


    /**
     * Validates a transfer request before it is turned into a transaction.
     *
     * @param request the incoming transfer request
     * @param responseObserver the observer used to report validation failures
     * @return {@code true} when the request is valid, {@code false} otherwise
     */
    public boolean validateTransferRequest(
        TransferRequest request,
        StreamObserver<TransferResponse> responseObserver
    ) {
        if (!isValidId(request.getSrcUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidSourceUserIdMessage)
                .asRuntimeException());
            return false;
        }

        if (!userBelongsToNodeOrganization(request.getSrcUserId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.UserDoesNotBelongToOrganizationMessage)
                .asRuntimeException());
            return false;
        }

        if (!isValidId(request.getSrcWalletId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidSourceWalletIdMessage)
                .asRuntimeException());
            return false;
        }

        if (!isValidId(request.getDstWalletId())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidDestinationWalletIdMessage)
                .asRuntimeException());
            return false;
        }

        if (request.getValue() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(ErrorMessage.InvalidTransferAmountMessage)
                .asRuntimeException());
            return false;
        }

        return true;
    }


    /**
     * Checks whether an identifier is non-null, trimmed, and strictly alphanumeric.
     *
     * @param id the identifier to validate
     * @return {@code true} when the identifier matches the expected format
     */
    private boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id.trim()).matches();
    }


    private boolean userBelongsToNodeOrganization(String userId) {
        return OrganizationUsers.contains(userId);
    }
}