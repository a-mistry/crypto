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

	public CompletableFuture<List<Account>> getAccounts()
		throws GdaxException
	{
		return submit("/accounts", "GET", null)
			.thenApply(jsonElement -> {
				JsonArray jsonArray = jsonElement.getAsJsonArray();
				List<Account> accounts = new ArrayList<>();
				for(JsonElement arrayElement : jsonArray)
					accounts.add(new Account(arrayElement.getAsJsonObject()));
				return accounts;
			});
	}

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

	public class GdaxException extends RuntimeException {
		private final int errorCode;
		private final String body;

		GdaxException(final int errorCode, final String body) {
			super("Error " + errorCode + ": " + body);
			this.errorCode = errorCode;
			this.body = body;
		}
	}

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

	public static void main(String[] args)
	{
		try {
			String apiFilename = MCProperties.getProperty("gdaxApiFile");
			log.debug("reading from " + apiFilename);
			Path apiFile = Paths.get(apiFilename);
			List<String> apiLines = Files.lines(apiFile).collect(Collectors.toList());
			GdaxClient client = new GdaxClient(apiLines.get(0), apiLines.get(1), apiLines.get(2));
			List<Account> accounts = client.getAccounts().get();
			for(Account account : accounts) {
				System.out.println(account.getId());
				System.out.println(account.getCurrency());
				System.out.println(account.getAvailable());
				System.out.println(account.getBalance());
				System.out.println(account.getHold());
			}
		} catch(Exception e) {
			log.error("error", e);
		}
	}
}
