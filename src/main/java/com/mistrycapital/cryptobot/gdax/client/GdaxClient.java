package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Asynchronous gdax REST client
 */
public class GdaxClient {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final URI apiURI;
	private final String apiKey;
	private final String passPhrase;
	private final Mac savedMac;
	private final HttpClient httpClient;
	private final JsonParser jsonParser;

	/**
	 * Create a new client
	 *
	 * @param apiURI     API base URL, e.g. https://api.gdax.com
	 * @param apiKey     key
	 * @param apiSecret  secret
	 * @param passPhrase pass phrase
	 */
	public GdaxClient(URI apiURI, String apiKey, String apiSecret, String passPhrase) {
		this.apiURI = apiURI;
		this.apiKey = apiKey;
		this.passPhrase = passPhrase;
		SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(apiSecret), "HmacSHA256");
		try {
			savedMac = Mac.getInstance(secretKeySpec.getAlgorithm());
			savedMac.init(secretKeySpec);
		} catch(NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
		httpClient = HttpClient.newHttpClient();
		jsonParser = new JsonParser();
	}

	/**
	 * Gets account balance and available funds information
	 *
	 * @return List of accounts
	 */
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
	 * Looks up information about the given order
	 *
	 * @param orderId Order id
	 * @return Order information
	 */
	public CompletableFuture<OrderInfo> getOrder(UUID orderId) {
		return submit("/orders/" + orderId, "GET", null)
			.thenApply(JsonElement::getAsJsonObject)
			.thenApply(OrderInfo::new);
	}

	/**
	 * Executes a market order
	 *
	 * @param product    Product to trade (e.g. BTC-USD)
	 * @param side       BUY if we should buy the first item in the pair
	 * @param sizingType SIZE if size is given, FUNDS if funds is given
	 * @param amount     The amount - either size or funds (based on sizingType)
	 * @return Execution returned by gdax
	 */
	public CompletableFuture<OrderInfo> placeMarketOrder(Product product, OrderSide side,
		MarketOrderSizingType sizingType,
		double amount)
	{
		JsonObject body = new JsonObject();
		body.addProperty("type", "market");
		body.addProperty("side", side == OrderSide.BUY ? "buy" : "sell");
		body.addProperty("product_id", product.toString());
		body.addProperty(sizingType == MarketOrderSizingType.SIZE ? "size" : "funds", Double.toString(amount));
		return submit("/orders", "POST", body)
			.thenApply(JsonElement::getAsJsonObject)
			.thenApply(OrderInfo::new);
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
		final URI uri = apiURI.resolve(path);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(uri)
			.method(method, isPost ? BodyPublisher.fromString(bodyString) : BodyPublisher.noBody())
			.header("User-Agent", "Custom")
			.header("Content-type", "application/json")
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
		final String toEncode = timestamp + method.toUpperCase(Locale.US) + path + body;
		try {
			Mac mac = (Mac) savedMac.clone();
			final byte[] encoded = mac.doFinal(toEncode.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encoded);
		} catch(CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
