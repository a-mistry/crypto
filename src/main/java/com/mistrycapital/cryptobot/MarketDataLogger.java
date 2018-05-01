package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.appender.*;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxWebSocket;
import com.mistrycapital.cryptobot.time.SystemTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;

import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MarketDataLogger {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args) throws Exception {
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		Path credentialsFile = Paths.get(properties.getProperty("credentialsFile"));
		log.debug("Reading credentials from " + credentialsFile);
		Properties credentials = new Properties();
		try(Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			credentials.load(reader);
		}

		final String BOOK_MESSAGE_FILE_NAME = properties.getProperty("output.filenameBase.bookMessages","gdax-orders");

		TimeKeeper timeKeeper = new SystemTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		FileAppender gdaxAppender =
			new GdaxMessageAppender(dataDir, BOOK_MESSAGE_FILE_NAME, ".json", timeKeeper, orderBookManager);
		gdaxAppender.open();
		GdaxWebSocket gdaxWebSocket = new GdaxWebSocket(timeKeeper, gdaxAppender);

		gdaxWebSocket.subscribe(orderBookManager);

		URI gdaxWebSocketURI = new URI("wss://ws-feed.gdax.com");
		WebSocketClient socketClient = new WebSocketClient(new SslContextFactory());

		while(true) {
			if(!gdaxWebSocket.isConnected()) {
				socketClient.stop();
				socketClient.start();
				ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
				socketClient.connect(gdaxWebSocket, gdaxWebSocketURI, upgradeRequest);
				log.info("Connecting to gdax feed: " + gdaxWebSocketURI);
				final long startMs = timeKeeper.epochMs();
				final long timeout = 30000L; // 30 seconds
				while(!gdaxWebSocket.isConnected() && timeKeeper.epochMs() - startMs < timeout) {
					Thread.sleep(100);
				}
				if(!gdaxWebSocket.isConnected()) {
					log.error("Timed out trying to connect to gdax websocket feed. Trying again in 5 seconds");
					Thread.sleep(5000L);
				}
			} else {
				Thread.sleep(1000);
			}
		}
	}
}
