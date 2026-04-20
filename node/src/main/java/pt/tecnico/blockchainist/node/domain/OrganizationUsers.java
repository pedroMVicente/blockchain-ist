package pt.tecnico.blockchainist.node.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import pt.tecnico.blockchainist.common.DebugLog;
import pt.tecnico.blockchainist.node.domain.exceptions.InitialStateCouldNotBeCreatedException;
import pt.tecnico.blockchainist.node.domain.message.ErrorMessage;
import pt.tecnico.blockchainist.node.domain.message.LogMessage;

/**
 * Static registry of user IDs belonging to this node's organization.
 *
 * <p>{@link #init(String)} must be called once at startup before any
 * other code queries the registry. After that, any class can call
 * {@link #contains(String)} to check membership.
 */
public final class OrganizationUsers {

	private static final String CLASSNAME = OrganizationUsers.class.getSimpleName();

	private static Set<String> users;

	private OrganizationUsers() {}

	/**
	 * Loads the organization's user set from {@code authorized_clients.json}.
	 * Must be called exactly once during startup.
	 *
	 * @param organization the organization identifier to filter by
	 * @throws InitialStateCouldNotBeCreatedException if the file cannot be read or parsed
	 */
	public static void init(String organization) throws InitialStateCouldNotBeCreatedException {
		InputStream inputStream = OrganizationUsers.class.getClassLoader()
			.getResourceAsStream("clients/authorized_clients.json");
		if (inputStream == null) {
			throw new InitialStateCouldNotBeCreatedException(
				ErrorMessage.CouldNotFindAuthorizedClientsJsonMessage
			);
		}

		final String content;
		try {
			content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new InitialStateCouldNotBeCreatedException(
				ErrorMessage.couldNotReadAuthorizedClientsJson(e.getMessage())
			);
		}

		final JsonArray clients;
		try {
			JsonElement root = JsonParser.parseString(content);
			if (!root.isJsonArray()) {
				throw new InitialStateCouldNotBeCreatedException(
					ErrorMessage.InvalidAuthorizedClientsJsonFormatMessage
				);
			}
			clients = root.getAsJsonArray();
		} catch (JsonParseException e) {
			throw new InitialStateCouldNotBeCreatedException(
				ErrorMessage.couldNotParseAuthorizedClientsJson(e.getMessage())
			);
		}

		Set<String> loaded = new HashSet<>();
		for (JsonElement clientElement : clients) {
			if (!clientElement.isJsonObject()) {
				throw new InitialStateCouldNotBeCreatedException(
					ErrorMessage.InvalidAuthorizedClientsJsonElementFormatMessage
				);
			}

			JsonObject client = clientElement.getAsJsonObject();
			JsonElement userIdElement = client.get("user_id");
			JsonElement organizationElement = client.get("organization");

			if (userIdElement == null || organizationElement == null ||
				!userIdElement.isJsonPrimitive() || !organizationElement.isJsonPrimitive()) {
				throw new InitialStateCouldNotBeCreatedException(
					ErrorMessage.InvalidAuthorizedClientsJsonObjectFormatMessage
				);
			}

			if (organization.equals(organizationElement.getAsString())) {
				loaded.add(userIdElement.getAsString());
			}
		}

		users = Collections.unmodifiableSet(loaded);
		DebugLog.log(CLASSNAME, LogMessage.organizationUsersFetchedSuccessfully(organization, users));
	}

	/**
	 * Returns whether the given user ID belongs to this node's organization.
	 *
	 * @param userId the user ID to check
	 * @return {@code true} if the user belongs to this organization
	 */
	public static boolean contains(String userId) {
		return users.contains(userId);
	}
}
