package pt.tecnico.blockchainist.client.grpc;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.crypto.spec.SecretKeySpec;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import pt.tecnico.blockchainist.client.domain.exceptions.ClientNodeServiceException;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.key.KeyManager;
import pt.tecnico.blockchainist.common.transaction.InternalTransaction;
import pt.tecnico.blockchainist.common.transaction.TransactionConverter;
import pt.tecnico.blockchainist.contract.CreateWalletRequest;
import pt.tecnico.blockchainist.contract.DeleteWalletRequest;
import pt.tecnico.blockchainist.contract.EncryptedRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateRequest;
import pt.tecnico.blockchainist.contract.GetBlockchainStateResponse;
import pt.tecnico.blockchainist.contract.NodeServiceGrpc;
import pt.tecnico.blockchainist.contract.ReadBalanceRequest;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.SignedCreateWalletRequest;
import pt.tecnico.blockchainist.contract.SignedDeleteWalletRequest;
import pt.tecnico.blockchainist.contract.SignedTransferRequest;
import pt.tecnico.blockchainist.contract.TransferRequest;
import pt.tecnico.blockchainist.client.domain.message.LogMessage;

/**
 * gRPC client stub wrapper for communicating with a single blockchain node.
 *
 * <p>Each instance manages one {@link ManagedChannel} to a specific node. It
 * provides both blocking and async variants for transactional operations
 * (wallet creation/deletion, transfers) and a blocking variant for read
 * operations (balance queries, blockchain state).
 *
 * <p>Every outgoing request is authenticated with an RSA/SHA-256 signature over
 * its raw protobuf bytes, attached as binary gRPC metadata. An artificial delay
 * hint (in seconds) is also forwarded as metadata for testing purposes; the node
 * uses it to simulate processing latency.
 *
 * <p>All {@link StatusRuntimeException}s thrown by the underlying stubs are caught
 * and re-thrown as {@link ClientNodeServiceException} so callers are insulated
 * from gRPC internals.
 *
 * <p>If the client private key cannot be loaded at construction time,
 * {@link #signRequest} will return {@code null} and every subsequent RPC will
 * carry a {@code null} signature, which the node will reject.
 */
public class ClientNodeService {

    private static final String CLASSNAME = ClientNodeService.class.getSimpleName();

    /** Maximum seconds to wait for in-flight calls to complete during shutdown. */
    private static final long SHUTDOWN_TIMEOUT = 5;

    /** Maximum seconds a node RPC may take before the client cancels it. */
    private static final int NODE_TIMEOUT = 15;

    private static final String CLIENT_SIGNATURE_PREFIX = "signatures/private";
    private static final String CLIENT_ENCRYPTION_PREFIX = "encryption/secret";
    /**
     * gRPC metadata key carrying the artificial delay hint (in seconds) for the
     * target node. Must match the key name expected by the node interceptor.
     */
    static final Metadata.Key<String> CLIENT_DELAY_KEY =
        Metadata.Key.of("delay_seconds", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final NodeServiceGrpc.NodeServiceBlockingStub stub;
    private final NodeServiceGrpc.NodeServiceStub stubAsync;
    private final String nodeAddress;

    /**
     * Opens a plaintext gRPC channel to the given node and loads the RSA private
     * key used to sign requests.
     *
     * <p>If the private key cannot be loaded, the failure is logged and
     * {@link #privateKey} remains {@code null}. All subsequent RPCs will carry a
     * {@code null} signature and be rejected by the node's authentication interceptor.
     *
     * @param host         the node's hostname or IP address
     * @param port         the node's gRPC port number
     * @param organization the organization identifier (reserved for future use)
     */
    public ClientNodeService(String host, int port, String organization) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();

        this.stub = NodeServiceGrpc.newBlockingStub(channel);
        this.stubAsync = NodeServiceGrpc.newStub(channel);
        this.nodeAddress = host + ":" + port;

    }

    /**
     * Shuts down the underlying gRPC channel, waiting up to
     * {@value #SHUTDOWN_TIMEOUT} seconds for any in-flight calls to complete.
     *
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting for the channel to terminate
     */
    public void shutdown() throws InterruptedException {
        DebugLog.log(CLASSNAME, LogMessage.shuttingDownChannel(nodeAddress));
        channel.shutdown().awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Returns a blocking stub with the delay hint and RSA signature attached as
     * metadata, and a {@value #NODE_TIMEOUT}-second deadline.
     *
     * <p>Used for transactional write operations that the caller wants to await
     * synchronously.
     *
     * @param delaySeconds   artificial delay hint to forward to the node
     * @return a configured blocking stub
     */
    private NodeServiceGrpc.NodeServiceBlockingStub transactionalStubWithDelay(
        int delaySeconds
    ) {
        Metadata metadata = new Metadata();
        metadata.put(CLIENT_DELAY_KEY, String.valueOf(delaySeconds));

        return stub
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
            .withDeadlineAfter(NODE_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Returns an async stub with the delay hint and RSA signature attached as
     * metadata, and a {@value #NODE_TIMEOUT}-second deadline.
     *
     * <p>Used for transactional write operations where the caller wants a
     * {@link CompletableFuture} rather than blocking.
     *
     * @param delaySeconds   artificial delay hint to forward to the node
     * @return a configured async stub
     */
    private NodeServiceGrpc.NodeServiceStub asyncTransactionalStubWithDelay(
        int delaySeconds
    ) {
        Metadata metadata = new Metadata();
        metadata.put(CLIENT_DELAY_KEY, String.valueOf(delaySeconds));

        return stubAsync
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
            .withDeadlineAfter(NODE_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Returns a blocking stub with only the delay hint attached as metadata, and
     * a {@value #NODE_TIMEOUT}-second deadline.
     *
     * <p>Used for read-only operations that do not require request authentication.
     *
     * @param delaySeconds artificial delay hint to forward to the node
     * @return a configured blocking stub without a signature header
     */
    private NodeServiceGrpc.NodeServiceBlockingStub nonTransactionalStubWithDelay(
        int delaySeconds
    ) {
        Metadata metadata = new Metadata();
        metadata.put(CLIENT_DELAY_KEY, String.valueOf(delaySeconds));

        return stub
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
            .withDeadlineAfter(NODE_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Requests the node to create a new wallet owned by the given user.
     *
     * <p>When {@code isBlocking} is {@code true}, the call blocks until the node
     * confirms the request has been sequenced and applied, and returns {@code null}.
     * When {@code false}, the call returns a {@link CompletableFuture} that
     * completes when the node's async response arrives.
     *
     * @param requestId     unique identifier for this request, used for deduplication
     * @param userId        the ID of the user who will own the new wallet
     * @param walletId      the ID to assign to the new wallet
     * @param delay_seconds artificial delay hint forwarded to the node
     * @param isBlocking    {@code true} to block until completion; {@code false} for async
     * @return a {@link CompletableFuture} that completes on success, or {@code null}
     *         if {@code isBlocking} is {@code true}
     * @throws ClientNodeServiceException if the node returns a gRPC error
     */
    public CompletableFuture<Void> createWallet(
        long requestId,
        String userId,
        String walletId,
        int delay_seconds,
        boolean isBlocking
    ) {

        // Create a CreateWalletRequest
        CreateWalletRequest request = CreateWalletRequest.newBuilder()
            .setRequestId(requestId)
            .setUserId(userId)
            .setWalletId(walletId)
            .build();

        byte[] signatureBytes = KeyManager.signRequest(
            getUserPrivateKey(userId),
            request.toByteArray()
        );

        DebugLog.log(CLASSNAME, LogMessage.createWalletRequestSent(nodeAddress, userId, walletId));

        // Sign the request
        SignedCreateWalletRequest signedRequest = SignedCreateWalletRequest.newBuilder()
            .setRequest(request)
            .setSignature(ByteString.copyFrom(signatureBytes))
            .build();

        // Encrypt the request
        byte[] iv = KeyManager.generateIV();

        EncryptedRequest encryptedRequest = EncryptedRequest.newBuilder()
            .setEncryptedPayload(ByteString.copyFrom(
                KeyManager.encrypt(getUserAESKey(userId), iv, signedRequest.toByteArray())
            ))
            .setIv(ByteString.copyFrom(iv))
            .setUserId(userId)
            .build();

        if (isBlocking) {
            try {
                transactionalStubWithDelay(delay_seconds).createWallet(
                    encryptedRequest
                );
            } catch (StatusRuntimeException e) {
                DebugLog.log(CLASSNAME, LogMessage.rpcError(
                    "createWallet",
                    e.getStatus().getCode().toString(),
                    e.getStatus().getDescription())
                );

                throw new ClientNodeServiceException(
                    e.getStatus().getDescription(),
                    e.getStatus().getCode()
                );
            }
            return null; // blocking callers ignore the return value
        } else {
            CompletableFuture<Void> future = new CompletableFuture<>();
            asyncTransactionalStubWithDelay(delay_seconds)
                .createWallet(encryptedRequest, new ClientObserver<>(future));
            return future;
        }
    }

    /**
     * Requests the node to delete an existing wallet owned by the given user.
     *
     * <p>Blocking and async semantics follow the same contract as
     * {@link #createWallet}.
     *
     * @param requestId     unique identifier for this request, used for deduplication
     * @param userId        the ID of the user who owns the wallet
     * @param walletId      the ID of the wallet to delete
     * @param delay_seconds artificial delay hint forwarded to the node
     * @param isBlocking    {@code true} to block until completion; {@code false} for async
     * @return a {@link CompletableFuture} that completes on success, or {@code null}
     *         if {@code isBlocking} is {@code true}
     * @throws ClientNodeServiceException if the node returns a gRPC error
     */
    public CompletableFuture<Void> deleteWallet(
        long requestId,
        String userId,
        String walletId,
        int delay_seconds,
        boolean isBlocking
    ) {

        // Create a DeleteWalletRequest
        DeleteWalletRequest request = DeleteWalletRequest.newBuilder()
            .setRequestId(requestId)
            .setUserId(userId)
            .setWalletId(walletId)
            .build();

        byte[] signatureBytes = KeyManager.signRequest(
            getUserPrivateKey(userId),
            request.toByteArray()
        );

        DebugLog.log(CLASSNAME, LogMessage.deleteWalletRequestSent(nodeAddress, userId, walletId));

        // Sign the request
        SignedDeleteWalletRequest signedRequest = SignedDeleteWalletRequest.newBuilder()
            .setRequest(request)
            .setSignature(ByteString.copyFrom(signatureBytes))
            .build();

        // Encrypt the request
        byte[] iv = KeyManager.generateIV();

        EncryptedRequest encryptedRequest = EncryptedRequest.newBuilder()
            .setEncryptedPayload(ByteString.copyFrom(
                KeyManager.encrypt(getUserAESKey(userId), iv, signedRequest.toByteArray())
            ))
            .setIv(ByteString.copyFrom(iv))
            .setUserId(userId)
            .build();

        if (isBlocking) {
            try {
                transactionalStubWithDelay(delay_seconds).deleteWallet(encryptedRequest);
            } catch (StatusRuntimeException e) {
                DebugLog.log(CLASSNAME,
                        LogMessage.rpcError(
                            "deleteWallet",
                            e.getStatus().getCode().toString(),
                            e.getStatus().getDescription())
                );
                throw new ClientNodeServiceException(
                    e.getStatus().getDescription(), e.getStatus().getCode()
                );
            }
            return null; // blocking callers ignore the return value
        } else {
            CompletableFuture<Void> future = new CompletableFuture<>();
            asyncTransactionalStubWithDelay(delay_seconds)
                .deleteWallet(encryptedRequest, new ClientObserver<>(future));
            return future;
        }
    }

    /**
     * Queries the node for the current balance of a wallet.
     *
     * <p>This is a synchronous read-only operation; no signature is attached.
     *
     * @param walletId      the ID of the wallet to query
     * @param delay_seconds artificial delay hint forwarded to the node
     * @return the wallet's current balance in the smallest currency unit
     * @throws ClientNodeServiceException if the node returns a gRPC error
     */
    public long readBalance(
        String walletId,
        int delay_seconds
    ) {
        ReadBalanceRequest request = ReadBalanceRequest.newBuilder()
            .setWalletId(walletId)
            .build();

        try {
            DebugLog.log(CLASSNAME, LogMessage.readBalanceRequestSent(nodeAddress, walletId));
            ReadBalanceResponse response = nonTransactionalStubWithDelay(delay_seconds).readBalance(request);
            DebugLog.log(CLASSNAME, LogMessage.readBalanceResponseReceived(walletId, response.getBalance()));
            return response.getBalance();
        } catch (StatusRuntimeException e) {
            DebugLog.log(CLASSNAME, LogMessage.rpcError(
                "readBalance",
                e.getStatus().getCode().toString(),
                e.getStatus().getDescription())
            );
            throw new ClientNodeServiceException(
                e.getStatus().getDescription(),
                e.getStatus().getCode()
            );
        }
    }

    /**
     * Requests the node to transfer funds from one wallet to another.
     *
     * <p>Blocking and async semantics follow the same contract as
     * {@link #createWallet}.
     *
     * @param requestId             unique identifier for this request, used for deduplication
     * @param sourceUserId          the ID of the user authorising the transfer
     * @param sourceWalletId        the ID of the wallet to debit
     * @param destinationWalletId   the ID of the wallet to credit
     * @param amount                the amount to transfer in the smallest currency unit
     * @param delay_seconds         artificial delay hint forwarded to the node
     * @param isBlocking            {@code true} to block until completion; {@code false} for async
     * @return a {@link CompletableFuture} that completes on success, or {@code null}
     *         if {@code isBlocking} is {@code true}
     * @throws ClientNodeServiceException if the node returns a gRPC error
     */
    public CompletableFuture<Void> transfer(
        long requestId,
        String sourceUserId,
        String sourceWalletId,
        String destinationWalletId,
        long amount,
        int delay_seconds,
        boolean isBlocking
    ) {
        // Create a TransferRequest
        TransferRequest request = TransferRequest.newBuilder()
            .setRequestId(requestId)
            .setSrcUserId(sourceUserId)
            .setSrcWalletId(sourceWalletId)
            .setDstWalletId(destinationWalletId)
            .setValue(amount)
            .build();

        byte[] signatureBytes = KeyManager.signRequest(
            getUserPrivateKey(sourceUserId),
            request.toByteArray()
        );

        DebugLog.log(CLASSNAME, LogMessage.transferRequestSent(
            nodeAddress,
            sourceUserId,
            sourceWalletId,
            destinationWalletId,
            amount)
        );

        // Sign the request
        SignedTransferRequest signedRequest = SignedTransferRequest.newBuilder()
            .setRequest(request)
            .setSignature(ByteString.copyFrom(signatureBytes))
            .build();

        // Encrypt the request
        byte[] iv = KeyManager.generateIV();

        EncryptedRequest encryptedRequest = EncryptedRequest.newBuilder()
            .setEncryptedPayload(ByteString.copyFrom(
                KeyManager.encrypt(getUserAESKey(sourceUserId), iv, signedRequest.toByteArray())
            ))
            .setIv(ByteString.copyFrom(iv))
            .setUserId(sourceUserId)
            .build();

        if (isBlocking) {
            try {
                transactionalStubWithDelay(delay_seconds).transfer(encryptedRequest);
            } catch (StatusRuntimeException e) {
                DebugLog.log(CLASSNAME, LogMessage.rpcError(
                            "transfer",
                            e.getStatus().getCode().toString(),
                            e.getStatus().getDescription())
                );
                throw new ClientNodeServiceException(
                    e.getStatus().getDescription(), e.getStatus().getCode()
                );
            }
            return null; // blocking callers ignore the return value
        } else {
            CompletableFuture<Void> future = new CompletableFuture<>();
            asyncTransactionalStubWithDelay(delay_seconds)
                .transfer(encryptedRequest, new ClientObserver<>(future));
            return future;
        }
    }

    /**
     * Retrieves the full ordered list of committed transactions from the node's
     * local blockchain state.
     *
     * <p>This is a synchronous read-only operation; no signature is attached.
     * The returned list reflects the node's view of the canonical chain at the
     * time the request is processed.
     *
     * @return an ordered list of {@link InternalTransaction} objects representing
     *         every transaction committed to the blockchain so far
     * @throws ClientNodeServiceException if the node returns a gRPC error
     */
    public List<InternalTransaction> getBlockchainState() {
        GetBlockchainStateRequest request = GetBlockchainStateRequest.newBuilder().build();

        try {
            DebugLog.log(CLASSNAME, LogMessage.getBlockchainStateRequestSent(nodeAddress));
            GetBlockchainStateResponse response = stub.getBlockchainState(request);
            DebugLog.log(CLASSNAME, LogMessage.getBlockchainStateResponseReceived(response.getTransactionsCount()));

            return response.getTransactionsList().stream()
                .map(TransactionConverter::convertToInternalTransaction)
                .collect(java.util.stream.Collectors.toList());
        } catch (StatusRuntimeException e) {
            DebugLog.log(CLASSNAME, LogMessage.rpcError(
                "getBlockchainState",
                e.getStatus().getCode().toString(),
                e.getStatus().getDescription())
            );
            throw new ClientNodeServiceException(
                e.getStatus().getDescription(),
                e.getStatus().getCode()
            );
        }
    }

    public PrivateKey getUserPrivateKey(
        String user
    ) {
        if (user == null || user.contains("..") || user.contains("/") || user.contains("\\")) {
            DebugLog.log(CLASSNAME, LogMessage.rejectedInvalidUserIdentifier(user));
            return null;
        }

        PrivateKey privateKey = null;
        try {
            privateKey = KeyManager.loadPrivateKey(
                getClass().getClassLoader(),
                CLIENT_SIGNATURE_PREFIX + user + KeyManager.RSA_KEY_EXTENSION
            );
        } catch (NoSuchAlgorithmException e) {
            DebugLog.log(CLASSNAME, LogMessage.rsaKeyFactoryUnavailable(e.getMessage()));

        } catch (InvalidKeySpecException e) {
            DebugLog.log(CLASSNAME, LogMessage.invalidOrMalformedRsaPrivateKeyForUser(user, e.getMessage()));

        } catch (IOException e) {
            DebugLog.log(CLASSNAME, LogMessage.failedToReadPrivateKeyForUser(user, e.getMessage()));
        }

        return privateKey;
    }

    private SecretKeySpec getUserAESKey(
        String user
    ){
        if (user == null || user.contains("..") || user.contains("/") || user.contains("\\")) {
            DebugLog.log(CLASSNAME, LogMessage.rejectedInvalidUserIdentifier(user));
            return null;
        }

        return KeyManager.loadAESKey(
            getClass().getClassLoader(),
            CLIENT_ENCRYPTION_PREFIX + user + KeyManager.AES_KEY_EXTENSION
        );
    }
}