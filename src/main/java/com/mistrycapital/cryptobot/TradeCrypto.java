package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.*;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.execution.GdaxExecutionEngine;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.GdaxPositionsProvider;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxWebSocket;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.sim.SnapshotReader;
import com.mistrycapital.cryptobot.tactic.OrderBookPeriodicEvaluator;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.tactic.TwoHourTactic;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.time.SystemTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.twilio.TwilioSender;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TradeCrypto {
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
		final String INTERVAL_FILE_NAME = properties.getProperty("output.filenameBase.samples", "samples");
		final String DECISION_FILE_NAME = properties.getProperty("output.filenameBase.decisions", "decisions");
		final String DAILY_FILE_NAME = properties.getProperty("output.filenameBase.daily", "daily");
		final String FORECAST_FILE_NAME = properties.getProperty("output.filenameBase.forecasts", "forecasts");

		TimeKeeper timeKeeper = new SystemTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		FileAppender gdaxAppender =
			new GdaxMessageAppender(dataDir, BOOK_MESSAGE_FILE_NAME, ".json", timeKeeper, orderBookManager);
		gdaxAppender.open();
		GdaxWebSocket gdaxWebSocket = new GdaxWebSocket(timeKeeper, gdaxAppender);
		GdaxClient gdaxClient = new GdaxClient(new URI("https://api.gdax.com"), credentials.getProperty("gdaxApiKey"),
			credentials.getProperty("gdaxApiSecret"), credentials.getProperty("gdaxPassPhrase"));
		GdaxPositionsProvider gdaxPositionsProvider = new GdaxPositionsProvider(timeKeeper, gdaxClient);
		Accountant accountant = new Accountant(gdaxPositionsProvider);
		Intervalizer intervalizer = new Intervalizer(properties);
		IntervalDataAppender intervalAppender = new IntervalDataAppender(dataDir, INTERVAL_FILE_NAME, timeKeeper);
		intervalAppender.open();
		DecisionAppender decisionAppender = new DecisionAppender(accountant, timeKeeper, dataDir, DECISION_FILE_NAME);
		decisionAppender.open();
		DailyAppender dailyAppender = new DailyAppender(accountant, timeKeeper, intervalizer, dataDir, DAILY_FILE_NAME);
		dailyAppender.open();
		ForecastAppender forecastAppender = new ForecastAppender(dataDir, FORECAST_FILE_NAME, timeKeeper);
		DynamicTracker dynamicTracker = new DynamicTracker();
		ForecastCalculator forecastCalculator = new Snowbird(properties);

		Tactic tactic = new TwoHourTactic(properties, timeKeeper, accountant);
		TradeRiskValidator tradeRiskValidator = new TradeRiskValidator(properties, timeKeeper, accountant);
		TwilioSender twilioSender =
			new TwilioSender(credentials.getProperty("TwilioAccountSid"), credentials.getProperty("TwilioAuthToken"));
		DBRecorder dbRecorder = new DBRecorder(timeKeeper, accountant, orderBookManager, "mistrycapital",
			credentials.getProperty("MysqlUser"), credentials.getProperty("MysqlPassword"));
		GdaxExecutionEngine executionEngine = new GdaxExecutionEngine(timeKeeper, accountant, orderBookManager,
			dbRecorder, twilioSender, tactic, gdaxClient);

		ConsolidatedHistory consolidatedHistory = restoreHistory(dataDir, INTERVAL_FILE_NAME, timeKeeper, intervalizer);
		warmupTactic(consolidatedHistory, forecastCalculator, tactic, intervalizer);

		OrderBookPeriodicEvaluator periodicEvaluator =
			new OrderBookPeriodicEvaluator(timeKeeper, intervalizer, consolidatedHistory, accountant, orderBookManager,
				dynamicTracker, intervalAppender, forecastCalculator, tactic, tradeRiskValidator, executionEngine,
				decisionAppender, dailyAppender, forecastAppender, dbRecorder);

		gdaxWebSocket.subscribe(orderBookManager);
		gdaxWebSocket.subscribe(dynamicTracker);
		gdaxWebSocket.subscribe(executionEngine);

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

	/** Creates and populates consolidated history for the past day (reads yesterday's and today's file) */
	private static ConsolidatedHistory restoreHistory(Path dataDir, String baseFilename, TimeKeeper timeKeeper,
		Intervalizer intervalizer)
		throws IOException
	{
		ConsolidatedHistory consolidatedHistory = new ConsolidatedHistory(intervalizer);

		ZonedDateTime today = ZonedDateTime.ofInstant(timeKeeper.now(), ZoneOffset.UTC);
		Path todayFile = getSnapshotFileIfExists(dataDir, baseFilename, today);
		Path yesterdayFile = getSnapshotFileIfExists(dataDir, baseFilename, today.minusDays(1));

		List<Path> files = new ArrayList<>(2);
		if(yesterdayFile != null) files.add(yesterdayFile);
		if(todayFile != null) files.add(todayFile);

		for(ConsolidatedSnapshot snapshot : SnapshotReader.readSnapshots(files))
			consolidatedHistory.add(snapshot);

		return consolidatedHistory;
	}

	private static void warmupTactic(ConsolidatedHistory fullHistory, ForecastCalculator forecastCalculator,
		Tactic tactic, Intervalizer intervalizer)
	{
		double[] historicalForecasts = new double[Product.count];
		ConsolidatedHistory partialHistory = new ConsolidatedHistory(intervalizer);
		for(ConsolidatedSnapshot snapshot : fullHistory.values()) {
			partialHistory.add(snapshot);
			for(Product product : Product.FAST_VALUES) {
				historicalForecasts[product.getIndex()] = forecastCalculator.calculate(partialHistory, product, null);
			}
			tactic.warmup(snapshot, historicalForecasts);
		}
		for(Product product : Product.FAST_VALUES) {
			log.debug("Last warmup forecast " + product + ": "
				+ forecastCalculator.calculate(fullHistory, product, null));
		}
	}

	private static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/** @return Snapshot file path for the given base and day if the file exists, null otherwise */
	private static Path getSnapshotFileIfExists(Path dataDir, String baseFilename, ZonedDateTime dateTime) {
		String filename = baseFilename + '-' + DATE_FORMATTER.format(dateTime) + ".csv";
		Path path = dataDir.resolve(filename);
		return Files.exists(path) ? path : null;
	}
}
