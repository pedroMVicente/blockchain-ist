package pt.tecnico.blockchainist.common.transaction;

import com.google.protobuf.ByteString;

import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.SignedTransaction;
import pt.tecnico.blockchainist.contract.Transaction;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.contract.SignedCreateWalletRequest;
import pt.tecnico.blockchainist.contract.SignedDeleteWalletRequest;
import pt.tecnico.blockchainist.contract.SignedTransferRequest;

/**
 * Utility class for converting between the gRPC-generated {@link Transaction} type
 * and the internal transaction hierarchy ({@link InternalTransaction} and its subclasses).
 */
public class TransactionConverter {

    private TransactionConverter() {}

    /**
     * Converts a gRPC {@link Transaction} to the appropriate {@link InternalTransaction} subclass.
     *
     * @param tx the gRPC transaction to convert
     * @return a {@link CreateWalletTransaction}, {@link DeleteWalletTransaction},
     *         or {@link TransferTransaction} depending on the operation type
     * @throws IllegalArgumentException if the transaction's operation type is unrecognised
     */
    public static InternalTransaction convertToInternalTransaction(Transaction tx) {
        switch (tx.getOperationCase()) {
            case CREATE_WALLET:
                return new CreateWalletTransaction(
                    tx.getCreateWallet().getRequestId(),
                    tx.getCreateWallet().getUserId(),
                    tx.getCreateWallet().getWalletId()
                );
            case DELETE_WALLET:
                return new DeleteWalletTransaction(
                    tx.getDeleteWallet().getRequestId(),
                    tx.getDeleteWallet().getUserId(),
                    tx.getDeleteWallet().getWalletId()
                );
            case TRANSFER:
                return new TransferTransaction(
                    tx.getTransfer().getRequestId(),
                    tx.getTransfer().getSrcUserId(),
                    tx.getTransfer().getSrcWalletId(),
                    tx.getTransfer().getDstWalletId(),
                    tx.getTransfer().getValue()
                );
            default:
                throw new IllegalArgumentException("Unknown transaction type: " + tx.getOperationCase());
        }
    }

    /**
     * Converts an {@link InternalTransaction} back to its gRPC {@link Transaction} representation.
     *
     * @param tx the internal transaction to wrap
     * @return the corresponding gRPC {@link Transaction}
     * @throws IllegalArgumentException if {@code tx} is not a recognised subclass
     */
    public static Transaction convertToTransaction(InternalTransaction tx) {
        if (tx instanceof CreateWalletTransaction) {
            CreateWalletTransaction cw = (CreateWalletTransaction) tx;
            return Transaction.newBuilder()
                .setCreateWallet(CreateWalletRequest.newBuilder()
                    .setRequestId(cw.getRequestId())
                    .setUserId(cw.getUserId())
                    .setWalletId(cw.getWalletId())
                    .build())
                .build();
        } else if (tx instanceof DeleteWalletTransaction) {
            DeleteWalletTransaction dw = (DeleteWalletTransaction) tx;
            return Transaction.newBuilder()
                .setDeleteWallet(DeleteWalletRequest.newBuilder()
                    .setRequestId(dw.getRequestId())
                    .setUserId(dw.getUserId())
                    .setWalletId(dw.getWalletId())
                    .build())
                .build();
        } else if (tx instanceof TransferTransaction) {
            TransferTransaction t = (TransferTransaction) tx;
            return Transaction.newBuilder()
                .setTransfer(TransferRequest.newBuilder()
                    .setRequestId(t.getRequestId())
                    .setSrcUserId(t.getUserId())
                    .setSrcWalletId(t.getWalletId())
                    .setDstWalletId(t.getDstWalletId())
                    .setValue(t.getValue())
                    .build())
                .build();
        } else {
            throw new IllegalArgumentException("Unknown InternalTransaction type: " + tx.getClass());
        }
    }

    public static SignedTransaction convertToSignedTransaction(SignedInternalTransaction tx){

        InternalTransaction internal_tx = tx.getInternalTransaction();
        if (internal_tx instanceof CreateWalletTransaction) {

            CreateWalletTransaction cw = (CreateWalletTransaction) internal_tx;
            CreateWalletRequest request = CreateWalletRequest.newBuilder()
                    .setRequestId(cw.getRequestId())
                    .setUserId(cw.getUserId())
                    .setWalletId(cw.getWalletId())
                    .build();

            SignedCreateWalletRequest signed_cw = SignedCreateWalletRequest.newBuilder()
                .setRequest(request)
                .setSignature(ByteString.copyFrom(tx.getSignature()))
                .build();

            return SignedTransaction.newBuilder()
                .setCreateWallet(signed_cw)
                .build();

        } else if (internal_tx instanceof DeleteWalletTransaction) {

            DeleteWalletTransaction dw = (DeleteWalletTransaction) internal_tx;
            DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
                    .setRequestId(dw.getRequestId())
                    .setUserId(dw.getUserId())
                    .setWalletId(dw.getWalletId())
                    .build();

            SignedDeleteWalletRequest signed_dw = SignedDeleteWalletRequest.newBuilder()
                .setRequest(request)
                .setSignature(ByteString.copyFrom(tx.getSignature()))
                .build();

            return SignedTransaction.newBuilder()
                .setDeleteWallet(signed_dw)
                .build();

        } else if (internal_tx instanceof TransferTransaction) {

            TransferTransaction t = (TransferTransaction) internal_tx;
            TransferRequest request = TransferRequest.newBuilder()
                    .setRequestId(t.getRequestId())
                    .setSrcUserId(t.getUserId())
                    .setSrcWalletId(t.getWalletId())
                    .setDstWalletId(t.getDstWalletId())
                    .setValue(t.getValue())
                    .build();

            SignedTransferRequest signed_t = SignedTransferRequest.newBuilder()
                .setRequest(request)
                .setSignature(ByteString.copyFrom(tx.getSignature()))
                .build();

            return SignedTransaction.newBuilder()
                .setTransfer(signed_t)
                .build();

        } else {
            throw new IllegalArgumentException("Unknown InternalTransaction type: " + tx.getClass());
        }
    }

    public static SignedInternalTransaction convertToSignedInternalTransaction(SignedTransaction tx) {
        InternalTransaction internalTx;
        byte[] signature;

        switch (tx.getOperationCase()) {
            case CREATE_WALLET:
                internalTx = new CreateWalletTransaction(
                    tx.getCreateWallet().getRequest().getRequestId(),
                    tx.getCreateWallet().getRequest().getUserId(),
                    tx.getCreateWallet().getRequest().getWalletId()
                );
                signature = tx.getCreateWallet().getSignature().toByteArray();
                break;
            case DELETE_WALLET:
                internalTx = new DeleteWalletTransaction(
                    tx.getDeleteWallet().getRequest().getRequestId(),
                    tx.getDeleteWallet().getRequest().getUserId(),
                    tx.getDeleteWallet().getRequest().getWalletId()
                );
                signature = tx.getDeleteWallet().getSignature().toByteArray();
                break;
            case TRANSFER:
                internalTx = new TransferTransaction(
                    tx.getTransfer().getRequest().getRequestId(),
                    tx.getTransfer().getRequest().getSrcUserId(),
                    tx.getTransfer().getRequest().getSrcWalletId(),
                    tx.getTransfer().getRequest().getDstWalletId(),
                    tx.getTransfer().getRequest().getValue()
                );
                signature = tx.getTransfer().getSignature().toByteArray();
                break;
            default:
                throw new IllegalArgumentException("Unknown transaction type: " + tx.getOperationCase());
        }

        return new SignedInternalTransaction(internalTx, signature);
    }
}
