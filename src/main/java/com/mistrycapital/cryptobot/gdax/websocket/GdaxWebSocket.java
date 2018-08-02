package com.mistrycapital.cryptobot.gdax.websocket;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.mistrycapital.cryptobot.gdax.common.Product;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage.Type;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;

import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;

@WebSocket(maxTextMessageSize = 4096 * 1024)
public class GdaxWebSocket extends SynchronousPublisher<GdaxMessage> {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final FileAppender fileAppender;
	private final HttpClient httpClient;
	private final JsonParser jsonParser;

	private final Queue<GdaxMessage>[] pending;
	private final AtomicBoolean[] building;
	private final AtomicLong[] sequence;

	private Session session;
	private volatile boolean connected;
	/** Latency of the websocket feed in microseconds */
	private long latencyMicros;
	/** Number of messages since last latency calc */
	private int latencyCalcCount;

	@SuppressWarnings("unchecked")
	public GdaxWebSocket(final TimeKeeper timeKeeper, final FileAppender fileAppender) {
		this.timeKeeper = timeKeeper;
		this.fileAppender = fileAppender;
		httpClient = HttpClient.newHttpClient();
		jsonParser = new JsonParser();
		pending = (Queue<GdaxMessage>[]) new Queue<?>[Product.count];
		building = new AtomicBoolean[Product.count];
		sequence = new AtomicLong[Product.count];
		for(Product product : Product.FAST_VALUES) {
			int index = product.getIndex();
			pending[index] = new ArrayDeque<GdaxMessage>(1000);
			building[index] = new AtomicBoolean(false);
			sequence[index] = new AtomicLong(0L);
		}
		connected = false;
	}

	public boolean isConnected() {
		return connected;
	}

	public void disconnect() {
		if(session != null)
			session.close();
		connected = false;
	}

	public long getLatencyMicros() {
		return latencyMicros;
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		connected = true;
		this.session = session;
		log.info("Connected to gdax websocket feed");
		try {
			JsonObject json = new JsonObject();
			json.addProperty("type", "subscribe");

			JsonArray productArray = new JsonArray();
			for(Product product : Product.FAST_VALUES) {
				productArray.add(product.toString());
			}
			json.add("product_ids", productArray);

			JsonArray channelArray = new JsonArray();
			channelArray.add("full");
			json.add("channels", channelArray);

			session.getRemote().sendString(json.toString());
			log.info("Subscribed to products");

			json = null; // mark for GC
			productArray = null;
			channelArray = null;
		} catch(IOException e) {
			log.error("Could not connect to gdax websocket feed", e);
		}
	}

	@OnWebSocketMessage
	public void onMessage(String msgStr) {
		try {
			fileAppender.append(msgStr);
			log.trace(msgStr);
		} catch(IOException e) {
			throw new RuntimeException("Could not write order message " + msgStr, e);
		}

		try {

			final GdaxMessage msg = parseMessage(msgStr);
			if(msg == null) {
				log.debug("Unknown msg: " + msgStr);
			} else {
				// We want to measure latency but it's not necessary to check every single time and incur
				// the method call penalty. Check every 2000 messages, which should amount to every few seconds
				if(latencyCalcCount > 2000) {
					latencyCalcCount = 0;
					latencyMicros = timeKeeper.epochNanos() / 1000L - msg.getTimeMicros();
				}
				latencyCalcCount++;

				Product product = msg.getProduct();
				final int index = product.getIndex();
				pending[index].add(msg);

				while(!pending[index].isEmpty() && !building[index].get()) {
					final GdaxMessage pendingMsg = pending[index].remove();
					if(sequence[index].get() < pendingMsg.getSequence() - 1) {
						// either just started or a gap in sequence, so we should rebuild the book
						startBuilding(product);

					} else if(pendingMsg.getSequence() < sequence[index].get()) {
						// ignore this message; it's out of date from our book
						log.trace("Skipping msg " + pendingMsg);

					} else {
						// we don't handle received or unknown messages right now
						if(pendingMsg.getType() != Type.UNKNOWN) {
							submit(pendingMsg);
						}
						sequence[index].set(pendingMsg.getSequence());
					}
				}
			}

		} catch(Exception e) {
			log.error("Error processing gdax message. Message was\n" + msgStr, e);
		}
	}

	GdaxMessage parseMessage(String msg) {
		JsonObject json = jsonParser.parse(msg).getAsJsonObject();
		String type = json.get("type").getAsString();
		GdaxMessage message = null;
		switch(type) {
			case "received":
				// can optimize this by only handling orders that are ours, but maybe we want the info
				// on other people's orders
				message = new Received(json);
				break;
			case "open":
				// new order on book
				message = new Open(json);
				break;
			case "done":
				// order taken off book - could be filled or canceled
				message = new Done(json);
				break;
			case "match":
				// trade occurred
				message = new Match(json);
				break;
			case "change":
				// order modified
				if(json.has("new_size")) {
					message = new ChangeSize(json);
				} else {
					message = new ChangeFunds(json);
				}
				break;
			case "activate":
				// new stop order
				message = new Activate(json);
				break;
			default:
				// this could be a new message type or received
				if(json.has("sequence") && json.has("product_id") && json.has("time")) {
					message = new Unknown(json);
				}
		}
		json = null; // mark for GC
		return message;
	}

	void startBuilding(final Product product)
		throws IOException
	{
		// start getting the book info if we are not already doing so
		if(building[product.getIndex()].compareAndSet(false, true)) {
			try {
				log.info("Building book for " + product);

				// note this in our files, so we can buffer in replaying
				JsonObject fakeMsg = new JsonObject();
				fakeMsg.addProperty("product_id", product.toString());
				fakeMsg.addProperty("time", timeKeeper.iso8601());
				fakeMsg.addProperty("type", "book_builder");
				fakeMsg.addProperty("sequence", sequence[product.getIndex()].get());
				fileAppender.append(fakeMsg.toString());

				// send request to build
				HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.gdax.com/products/" + product + "/book?level=3"))
					.GET()
					.header("User-Agent", "Custom")
					.build();
				httpClient.sendAsync(request, HttpResponse.BodyHandler.asString())
					.thenAccept(response -> {
						if(response.statusCode() != 200) {
							throw new RuntimeException(
								"Could not build book for " + product + " code " + response.statusCode() + ": " +
									response.body());
						}
						final String body = response.body();
						JsonObject json = jsonParser.parse(body).getAsJsonObject();
						// save additional info with message for data replay
						json.addProperty("product_id", product.toString());
						json.addProperty("time", timeKeeper.iso8601());
						json.addProperty("type", "book");
						try {
							fileAppender.append(json.toString());
						} catch(IOException e) {
							throw new RuntimeException("Could not write level 3 book data " + body, e);
						}
						Book message = new Book(json);
						json = null; // mark for GC
						submit(message);
						sequence[product.getIndex()].set(message.getSequence());
						building[product.getIndex()].set(false);
						message = null; // mark for GC
					});

			} catch(URISyntaxException e) {
				throw new RuntimeException("Could not build book for " + product, e);
			}
		}
	}

	@OnWebSocketError
	public void onError(Throwable error) {
		log.error("Gdax websocket error", error);
		disconnect();
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		log.info("Gdax websocket connection closed " + statusCode + ": " + reason);
		session = null;
		connected = false;
	}

	public void subscribe(GdaxMessageProcessor processor) {
		subscribe(new GdaxMessageProcessingSubscriber(processor));
	}
}