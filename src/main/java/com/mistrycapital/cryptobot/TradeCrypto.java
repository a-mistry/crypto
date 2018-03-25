package com.mistrycapital.cryptobot;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;

import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.appender.GdaxMessageAppender;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxWebSocket;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.SystemTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;

import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;

public class TradeCrypto {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final String DATA_FILE_NAME = "gdax-orders";
	private static final String DATA_FILE_EXTENSION = ".json";

	public static void main(String[] args)
		throws Exception
	{
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(new URI("https://api.gdax.com/products"))
			.GET()
			.header("User-Agent", "Custom")
			.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandler.asString());
		if(response.statusCode() == 200) {
			System.out.println("Worked. Response is ");
			System.out.println(response.body());
		} else {
			System.out.println("not working err=" + response.statusCode());
			System.out.println(response.body());
		}
		TimeKeeper timeKeeper = new SystemTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);

		WebSocketClient socketClient = new WebSocketClient(new SslContextFactory());
		final String dataDirString = MCProperties.getProperty("dataDir");
		log.debug("Saving message data to " + dataDirString);
		Path dataDir = Paths.get(dataDirString);
		FileAppender fileAppender =
			new GdaxMessageAppender(dataDir, DATA_FILE_NAME, DATA_FILE_EXTENSION, timeKeeper, orderBookManager);
		GdaxWebSocket socket = new GdaxWebSocket(timeKeeper, fileAppender);

		socket.subscribe(orderBookManager);

		socketClient.start();
		URI echoUri = new URI("wss://ws-feed.gdax.com");
		ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
		socketClient.connect(socket, echoUri, upgradeRequest);
		System.out.printf("Connecting to : %s%n", echoUri);

		while(true) {
			Thread.sleep(60000);
			log.debug("Heartbeat");
		}
	}

}
