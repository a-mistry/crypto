package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.appender.GdaxMessageAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.execution.Aggression;
import com.mistrycapital.cryptobot.execution.ChasingGdaxExecutionEngine;
import com.mistrycapital.cryptobot.execution.GdaxExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.GdaxPositionsProvider;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxWebSocket;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.time.SystemTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.twilio.TwilioSender;
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
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class SendExecutionTestTrades {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		Path credentialsFile = Paths.get(properties.getProperty("credentialsFile"));
		log.debug("Reading credentials from " + credentialsFile);
		Properties credentials = new Properties();
		try(Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			credentials.load(reader);
		}

		final String BOOK_MESSAGE_FILE_NAME = properties.getProperty("output.filenameBase.bookMessages", "gdax-orders");

		TimeKeeper timeKeeper = new SystemTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		FileAppender gdaxAppender =
			new GdaxMessageAppender(dataDir, BOOK_MESSAGE_FILE_NAME, ".json", timeKeeper, orderBookManager);
		gdaxAppender.open();
		GdaxWebSocket gdaxWebSocket = new GdaxWebSocket(timeKeeper, gdaxAppender);
		GdaxClient gdaxClient = new GdaxClient(new URI("https://api.gdax.com"), credentials.getProperty("gdaxApiKey"),
			credentials.getProperty("gdaxApiSecret"), credentials.getProperty("gdaxPassPhrase"));
		GdaxPositionsProvider gdaxPositionsProvider = new GdaxPositionsProvider(timeKeeper, gdaxClient);
		Accountant accountant = new DummyAccountant(gdaxPositionsProvider);

		Tactic tactic = new DummyTactic();
		TwilioSender twilioSender =
			new TwilioSender(credentials.getProperty("TwilioAccountSid"), credentials.getProperty("TwilioAuthToken"));
		DBRecorder dbRecorder = new DBRecorder(timeKeeper, accountant, orderBookManager, "mistrycapital",
			credentials.getProperty("MysqlUser"), credentials.getProperty("MysqlPassword"));
		ChasingGdaxExecutionEngine executionEngine = new ChasingGdaxExecutionEngine(accountant, orderBookManager,
			dbRecorder, twilioSender, tactic, gdaxClient);

		gdaxWebSocket.subscribe(orderBookManager);
		gdaxWebSocket.subscribe(executionEngine);

		URI gdaxWebSocketURI = new URI("wss://ws-feed.gdax.com");
		WebSocketClient socketClient = new WebSocketClient(new SslContextFactory());

		socketClient.start();
		ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
		socketClient.connect(gdaxWebSocket, gdaxWebSocketURI, upgradeRequest);
		log.info("Connecting to gdax feed: " + gdaxWebSocketURI);

		Thread.sleep(20000); // wait for order books to be built

		TradeInstruction instruction =
			new TradeInstruction(Product.LTC_USD, 0.001, OrderSide.BUY, Aggression.POST_ONLY, 0.01);
		log.debug("Instruction object is " + instruction);
		executionEngine.trade(Collections.singletonList(instruction));

		Thread.sleep(3000000); // do nothing for 50 min

		log.info("Shutting down");
		System.exit(1);
	}

	static class DummyTactic implements Tactic {

		@Override
		public List<TradeInstruction> decideTrades(final ConsolidatedSnapshot snapshot, final double[] forecasts) {
			return null;
		}

		@Override
		public void notifyFill(final TradeInstruction instruction, final Product product, final OrderSide orderSide,
			final double amount,
			final double price)
		{
			log.info("FILL RECEIVED " + instruction + " " + amount + " " + orderSide + " " + product + " at " + price);
		}

		@Override
		public void notifyReject(final TradeInstruction instruction) {
			log.info("REJECTED ORDER " + instruction);
		}

		@Override
		public void warmup(final ConsolidatedSnapshot snapshot, final double[] forecasts) {

		}
	}

	static class DummyAccountant extends Accountant {

		@Override
		public synchronized void recordTrade(Currency source, double sourcePosChange, Currency dest,
			double destPosChange)
		{
			log.info("RECORDED TRADE " + source + " " + sourcePosChange + " " + dest + " " + destPosChange);
		}

		public DummyAccountant(final PositionsProvider positionsProvider) {
			super(positionsProvider);
		}
	}
}
