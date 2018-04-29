package com.mistrycapital.cryptobot;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.appender.*;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.GdaxExecutionEngine;
import com.mistrycapital.cryptobot.forecasts.Brighton;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.gdax.GdaxClientFactory;
import com.mistrycapital.cryptobot.gdax.GdaxPositionsProvider;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.tactic.OrderBookPeriodicEvaluator;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.time.Intervalizer;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;

import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxWebSocket;
import com.mistrycapital.cryptobot.time.SystemTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;

public class TradeCrypto {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final String BOOK_MESSAGE_FILE_NAME = "gdax-orders";
	public static final String INTERVAL_FILE_NAME = "samples";
	public static final String FORECAST_FILE_NAME = "forecasts";
	public static final String DECISION_FILE_NAME = "decisions";
	public static final String DAILY_FILE_NAME = "daily";

	public static void main(String[] args)
		throws Exception
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		String apiFilename = properties.getProperty("gdax.client.apiFile");
		Path gdaxApiFile = Paths.get(apiFilename);
		log.debug("Reading gdax credentials from " + apiFilename);

		TimeKeeper timeKeeper = new SystemTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		FileAppender gdaxAppender =
			new GdaxMessageAppender(dataDir, BOOK_MESSAGE_FILE_NAME, ".json", timeKeeper, orderBookManager);
		gdaxAppender.open();
		GdaxWebSocket gdaxWebSocket = new GdaxWebSocket(timeKeeper, gdaxAppender);
		GdaxClient gdaxClient = GdaxClientFactory.createClientFromFile(gdaxApiFile);
		GdaxPositionsProvider gdaxPositionsProvider = new GdaxPositionsProvider(timeKeeper, gdaxClient);
		Accountant accountant = new Accountant(gdaxPositionsProvider);
		Intervalizer intervalizer = new Intervalizer(properties);
		IntervalDataAppender intervalAppender = new IntervalDataAppender(dataDir, INTERVAL_FILE_NAME, timeKeeper);
		intervalAppender.open();
		DecisionAppender decisionAppender = new DecisionAppender(accountant, timeKeeper, dataDir, DECISION_FILE_NAME);
		decisionAppender.open();
		DailyAppender dailyAppender = new DailyAppender(accountant, timeKeeper, intervalizer, dataDir, DAILY_FILE_NAME);
		dailyAppender.open();
		DynamicTracker dynamicTracker = new DynamicTracker();
		ForecastCalculator forecastCalculator = new Brighton(properties);

		Tactic tactic = new Tactic(properties, accountant);
		TradeRiskValidator tradeRiskValidator = new TradeRiskValidator(properties, timeKeeper, accountant);
		ExecutionEngine executionEngine = new GdaxExecutionEngine(timeKeeper, accountant, orderBookManager, gdaxClient);
		OrderBookPeriodicEvaluator periodicEvaluator =
			new OrderBookPeriodicEvaluator(timeKeeper, intervalizer, accountant, orderBookManager, dynamicTracker,
				intervalAppender, forecastCalculator, tactic, tradeRiskValidator, executionEngine, decisionAppender,
				dailyAppender);

		gdaxWebSocket.subscribe(orderBookManager);
		gdaxWebSocket.subscribe(dynamicTracker);

		URI gdaxWebSocketURI = new URI("wss://ws-feed.gdax.com");
		WebSocketClient socketClient = new WebSocketClient(new SslContextFactory());

		Thread t = new Thread(periodicEvaluator);
		t.setDaemon(true);
		t.start();

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
//			// kill connection after 10s to test
//			Thread.sleep(10000L);
//			gdaxWebSocket.disconnect();
//			Thread.sleep(5000L);
		}
	}

}
