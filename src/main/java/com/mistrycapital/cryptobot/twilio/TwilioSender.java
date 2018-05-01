package com.mistrycapital.cryptobot.twilio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import org.slf4j.Logger;

import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Sends message asynchronously via twilio REST API. Note that we built this out because of conflicts with
 * the twilio sdk and java 9.
 */
public class TwilioSender {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final String accountSid;
	private final String authToken;
	private final HttpClient httpClient;
	private final URI messagesURI;
	private final JsonParser jsonParser;

	public TwilioSender(String accountSid, String authToken) {
		this.accountSid = accountSid;
		this.authToken = authToken;
		httpClient = HttpClient.newBuilder()
			.authenticator(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(accountSid, authToken.toCharArray());
				}
			})
			.build();
		try {
			messagesURI = new URI("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json");
		} catch(URISyntaxException e) {
			log.error("Invalid messages uri in twilio", e);
			throw new RuntimeException(e);
		}
		jsonParser = new JsonParser();
	}

	public void sendMessage(String msg) {
		final String to = "+19144660063";
		final String from = "+16469923687";
		final String bodyString = "From=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
			+ "&To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
			+ "&Body=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);
		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(messagesURI)
			.header("User-Agent", "Custom")
			.header("Content-Type", "application/x-www-form-urlencoded")
			.method("POST", HttpRequest.BodyPublisher.fromString(bodyString))
			.build();
		httpClient.sendAsync(httpRequest, HttpResponse.BodyHandler.asString())
			.thenAccept(response -> {
				try {
					JsonObject json = jsonParser.parse(response.body()).getAsJsonObject();
					log.info("Sent twilio msg id " + json.get("sid").getAsString() + ": " + msg);
				} catch(Exception e) {
					log.error("Error in handling twilio response for msg " + msg, e);
				}
			});
	}
}
