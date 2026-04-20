package pt.tecnico.blockchainist.node.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.crypto.spec.SecretKeySpec;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.common.key.KeyManager;
import pt.tecnico.blockchainist.contract.BlockRequest;
import pt.tecnico.blockchainist.contract.BlockResponse;
import pt.tecnico.blockchainist.contract.BroadcastRequest;
import pt.tecnico.blockchainist.contract.EncryptedRequest;
import pt.tecnico.blockchainist.contract.EncryptedResponse;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc;
import pt.tecnico.blockchainist.contract.SequencerServiceGrpc.SequencerServiceBlockingStub;
import pt.tecnico.blockchainist.contract.SignedTransaction;
import pt.tecnico.blockchainist.contract.IsStartingUpRequest;
import pt.tecnico.blockchainist.contract.IsStartingUpResponse;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;

/**
 * Blocking gRPC client used by a node to communicate with the central sequencer.
 *
 * <p>This client encapsulates channel creation, synchronous RPC invocation, and
 * shutdown of the connection to the sequencer service.
 */
class ClientSequencerService implements AutoCloseable {

	private static final String CLASSNAME = ClientSequencerService.class.getClass().getSimpleName();
	/** Timeout in seconds for channel shutdown. */
	private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

	/** gRPC channel to the sequencer service. */
	private final ManagedChannel channel;

	/** Blocking stub used for synchronous RPC calls to the sequencer. */
	private final SequencerServiceBlockingStub stub;

	private SecretKeySpec sequencerAESKey;

	/**
	 * Creates a client connected to the given sequencer endpoint.
	 *
	 * @param host the sequencer hostname or IP address
	 * @param port the sequencer gRPC port
	 */
	ClientSequencerService(
		String host,
		int port
	) {
		this.channel = ManagedChannelBuilder.forAddress(host, port)
			.usePlaintext()
			.build();

		this.stub = SequencerServiceGrpc.newBlockingStub(channel);

		this.sequencerAESKey = KeyManager.loadAESKey(
			getClass().getClassLoader(),
			"sequencer/encryption/secretSequencer.key"
		);

		if (this.sequencerAESKey == null) {
			throw new IllegalStateException(
				ErrorMessage.FailedToLoadSequencerAESKeyMessage("sequencer/encryption/secretSequencer.key")
			);
		}
	}


	/**
	 * Broadcasts a transaction to the sequencer so it can be accepted into
	 * the global order. The dependency list tells the sequencer which
	 * request IDs must appear in the blockchain before this transaction.
	 *
	 * @param transaction  the transaction to broadcast
	 * @param dependencies request IDs that must be sequenced first (at most 2)
	 */
	void broadcastTransaction(
		SignedTransaction transaction,
		List<Long> dependencies
	) {
		BroadcastRequest request = BroadcastRequest.newBuilder()
            .setTransaction(transaction)
			.addAllDependsOn(dependencies)
            .build();

		byte[] iv = KeyManager.generateIV();

		EncryptedRequest encryptedRequest = EncryptedRequest.newBuilder()
			.setEncryptedPayload(
				ByteString.copyFrom(KeyManager.encrypt(
					this.sequencerAESKey, iv, request.toByteArray()
				)))
			.setIv(ByteString.copyFrom(iv))
			.build();

        stub.broadcast(encryptedRequest);
    }


	/**
	 * Requests the closed block with the given block number from the sequencer.
	 *
	 * @param blockNumber the block number to fetch
	 * @return the sequencer response containing the requested block
	 * @throws InvalidProtocolBufferException
	 */
    BlockResponse deliverBlock(
		int blockNumber
	) throws InvalidProtocolBufferException {

		BlockRequest request = BlockRequest.newBuilder()
            .setBlockNumber(blockNumber)
            .build();

		byte[] iv = KeyManager.generateIV();

		EncryptedRequest encryptedRequest = EncryptedRequest.newBuilder()
			.setEncryptedPayload(ByteString.copyFrom(
				KeyManager.encrypt(sequencerAESKey, iv, request.toByteArray())
			))
			.setIv(ByteString.copyFrom(iv))
			.build();

        EncryptedResponse encryptedResponse = stub.deliverBlock(encryptedRequest);

		// Decrypt message
		byte[] decryptedBytes = KeyManager.decrypt(
			this.sequencerAESKey,
			encryptedResponse.getIv().toByteArray(),
			encryptedResponse.getEncryptedPayload().toByteArray()
		);

		DebugLog.log(CLASSNAME, LogMessage.sequencerAesKeyIsNull(this.sequencerAESKey == null));
		DebugLog.log(CLASSNAME, LogMessage.ivLength(encryptedRequest.getIv().toByteArray().length));
		DebugLog.log(CLASSNAME, LogMessage.payloadLength(encryptedRequest.getEncryptedPayload().toByteArray().length));
		DebugLog.log(CLASSNAME, LogMessage.decryptedBytesAreNull(decryptedBytes == null));

		// Parse the decrypted bytes
		return BlockResponse.parseFrom(decryptedBytes);
    }

	/**
	 * Checks if the sequencer is starting up.
	 *
	 * @return true if the sequencer is starting up, false otherwise
	 */
	boolean isStartingUp() {
		IsStartingUpResponse response = stub.isStartingUp(IsStartingUpRequest.newBuilder().build());
		return response.getIsStartingUp();
	}

	/**
	 * Closes this client by delegating to {@link #shutdown()}.
	 *
	 * @throws InterruptedException if shutdown is interrupted
	 */
	@Override
	public void close() throws InterruptedException {
		shutdown();
	}

	/**
	 * Shuts down the gRPC channel to the sequencer.
	 *
	 * @throws InterruptedException if the shutdown is interrupted
	 */
	void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}
}
