package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpRequest.BodyPublisher;
import jdk.incubator.http.HttpResponse;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Asynchronous gdax REST client
 */
public class GdaxClient {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final HttpClient httpClient;
	private final String apiKey;
	private final Key secretKeySpec;
	private final String passPhrase;
	private final JsonParser jsonParser;

	public GdaxClient(String apiKey, String apiSecret, String passPhrase) {
		httpClient = HttpClient.newHttpClient();
		this.apiKey = apiKey;
		this.secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(apiSecret), "HmacSHA256");
		this.passPhrase = passPhrase;
		jsonParser = new JsonParser();
	}

	public CompletableFuture<List<Account>> getAccounts() {
		return submit("/accounts", "GET", null)
			.thenApply(jsonElement -> {
				JsonArray jsonArray = jsonElement.getAsJsonArray();
				List<Account> accounts = new ArrayList<>();
				for(JsonElement arrayElement : jsonArray)
					accounts.add(new Account(arrayElement.getAsJsonObject()));
				return accounts;
			});
	}

	/**
	 * Submits the request to the gdax API asynchronously
	 *
	 * @param path   URI path, e.g. /accounts
	 * @param method GET or POST
	 * @param body   Body for a POST request or null for a GET
	 * @return Future with json returned by the API, which throws GdaxException if there was an error
	 */
	private CompletableFuture<JsonElement> submit(String path, String method, JsonObject body) {
		final boolean isPost = method.equals("POST");
		final String bodyString = isPost ? body.toString() : "";
		final long timestamp = System.currentTimeMillis() / 1000;

		final URI uri;
		try {
			uri = new URI("https://api.gdax.com" + path);
		} catch(URISyntaxException e) {
			throw new RuntimeException(e);
		}

		HttpRequest request = HttpRequest.newBuilder()
			.uri(uri)
			.method(method, isPost ? BodyPublisher.fromString(bodyString) : BodyPublisher.noBody())
			.header("User-Agent", "Custom")
			.header("CB-ACCESS-SIGN", getSignature(timestamp, path, method, bodyString))
			.header("CB-ACCESS-TIMESTAMP", Long.toString(timestamp))
			.header("CB-ACCESS-KEY", apiKey)
			.header("CB-ACCESS-PASSPHRASE", passPhrase)
			.build();
		return httpClient.sendAsync(request, HttpResponse.BodyHandler.asString())
			.thenApply(response -> {
				if(response.statusCode() / 100 == 2) {
					return jsonParser.parse(response.body());
				} else {
					throw new GdaxException(response.statusCode(), response.body());
				}
			});
	}

	/** HTTP error */
	public class GdaxException extends RuntimeException {
		private final int errorCode;
		private final String body;

		GdaxException(final int errorCode, final String body) {
			super("Error " + errorCode + ": " + body);
			this.errorCode = errorCode;
			this.body = body;
		}

		public final int getErrorCode() {
			return errorCode;
		}

		public final String getBody() {
			return body;
		}
	}

	/**
	 * @param timestamp Unix timestamp in seconds
	 * @param path      URI path, for example /accounts
	 * @param method    GET or POST
	 * @param body      Body for POST request, or "" for GET
	 * @return CB-ACCESS-SIGN signature
	 */
	private String getSignature(final long timestamp, final String path, final String method, final String body) {
		final String toEncode = timestamp + method + path + body;
		try {
			Mac mac = Mac.getInstance(secretKeySpec.getAlgorithm());
			mac.init(secretKeySpec);
			final byte[] encoded = mac.doFinal(toEncode.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encoded);
		} catch(InvalidKeyException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
