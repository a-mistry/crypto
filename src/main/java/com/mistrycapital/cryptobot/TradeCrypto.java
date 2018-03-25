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

		socket.subscribe(new Subscriber<>() {
			Subscription subscription;

			@Override
			public void onComplete() {
			}

			@Override
			public void onError(Throwable e) {
			}

			@Override
			public void onNext(GdaxMessage msg) {
				subscription.request(1);
			}

			@Override
			public void onSubscribe(Subscription subscription) {
				this.subscription = subscription;
				subscription.request(1);
			}
		});
		socket.subscribe(orderBookManager);

		socketClient.start();
		URI echoUri = new URI("wss://ws-feed.gdax.com");
		ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
		socketClient.connect(socket, echoUri, upgradeRequest);
		System.out.printf("Connecting to : %s%n", echoUri);

		//long startMs = System.currentTimeMillis();
		//while(System.currentTimeMillis() - startMs < 60000L) {
		while(true) {
			Thread.sleep(1000);
//        	OrderBook btcBook = orderBookManager.getBook(Product.BTC_USD);
//        	BBO bbo = btcBook.getBBO();
//        	log.debug("BTC " + bbo.bidPrice + " (" + bbo.bidSize + ") " + bbo.askPrice + " (" + bbo.askSize + ") ");
		}
		//socket.close();
		//System.exit(0);
	}

}
