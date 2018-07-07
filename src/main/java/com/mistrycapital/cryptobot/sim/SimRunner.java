package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.accounting.EmptyPositionsProvider;
import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.appender.DailyAppender;
import com.mistrycapital.cryptobot.appender.DecisionAppender;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.BBOProvider;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.ForecastFactory;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.tactic.TradeEvaluator;
import com.mistrycapital.cryptobot.tactic.TwoHourTactic;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SimRunner implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Intervalizer intervalizer;
	private final Path dataDir;
	private final double[] forecasts;
	private final String decisionFile;
	private final String dailyFile;
	private final String searchFile;
	private final String forecastFile;
	private final int startingUsd;
	private final ParameterOptimizer parameterOptimizer;
	private final String searchObjective;

	public SimRunner(MCProperties properties, Path dataDir)
	{
		this.dataDir = dataDir;
		intervalizer = new Intervalizer(properties);
		forecasts = new double[Product.count];
		decisionFile = properties.getProperty("sim.decisionFile", "decisions.csv");
		dailyFile = properties.getProperty("sim.dailyFile", "daily.csv");
		searchFile = properties.getProperty("sim.searchFile", "search.csv");
		forecastFile = properties.getProperty("sim.forecastFile", "forecasts.csv");
		startingUsd = properties.getIntProperty("sim.startUsd", 10000);
		parameterOptimizer = new ParameterOptimizer();
		searchObjective = properties.getProperty("sim.searchObjective", "return");
	}

	@Override
	public void run() {
		try {

			long startNanos = System.nanoTime();
			List<ConsolidatedSnapshot> consolidatedSnapshots =
				SnapshotReader.readSnapshots(SnapshotReader.getSampleFiles(dataDir));
			log.info("Reading snapshots took " + ((System.nanoTime() - startNanos) / 1000000000.0) + "sec");
			startNanos = System.nanoTime();
			MCProperties simProperties = new MCProperties();
			Map<Long,List<Double>> forecastCache = cacheForecasts(consolidatedSnapshots, simProperties);
			log.info("Caching forecasts took " + ((System.nanoTime() - startNanos) / 1000000000.0) + "sec");

			boolean search = simProperties.getBooleanProperty("sim.searchParameters", false);
			if(search) {
				simProperties.put("sim.logDecisions", "false");
				simProperties.put("tactic.buyForecastProportion", "false");
				List<ParameterSearchSpace> searchList = Arrays.asList(
					new ParameterSearchSpace("tactic.buyThreshold", 0.001, 0.015, 100)
//					new ParameterSearchSpace("tactic.buyUpperThreshold", 0.01, 0.02, 6),
//					new ParameterSearchSpace("tactic.tradeScaleFactor", 1.0, 10.0, 10)
				);
				try(BufferedWriter writer = Files.newBufferedWriter(dataDir.resolve(searchFile))) {
					for(var searchSpace : searchList) {
						writer.append(searchSpace.parameterName);
						writer.append(',');
					}
					writer.append(SimResult.csvHeaderRow());
					writer.append('\n');
					SimResult maxResult = parameterOptimizer.optimize(simProperties,
						searchList,
						properties -> {
							try {
								SimResult result = simulate(consolidatedSnapshots, forecastCache, properties);
								for(var searchSpace : searchList) {
									double paramValue = properties.getDoubleProperty(searchSpace.parameterName);
									writer.append(GdaxClient.gdaxDecimalFormat.format(paramValue));
									writer.append(',');
								}
								writer.append(result.toCSVString());
								writer.append('\n');
								writer.flush();
								return result;
							} catch(IOException e) {
								log.error("IO error running simulation", e);
								throw new RuntimeException(e);
							}
						}
					);
					log.info("Max return of " + maxResult.holdingPeriodReturn + " sharpe of " + maxResult.sharpeRatio
						+ " achieved at"
						+ " buyThreshold=" + simProperties.getDoubleProperty("tactic.buyThreshold")
						+ " tradeUsdThreshold=" + simProperties.getDoubleProperty("tactic.tradeUsdThreshold")
						+ " tradeScaleFactor=" + simProperties.getDoubleProperty("tactic.tradeScaleFactor"));
				}
			}
			simProperties.put("sim.logDecisions", "true");
			startNanos = System.nanoTime();
			SimResult result = simulate(consolidatedSnapshots, forecastCache, simProperties);
			log.info("Simulation treatment ended in " + ((System.nanoTime() - startNanos) / 1000000.0) + "ms");
			log.info("Tactic buyThreshold=" + simProperties.getDoubleProperty("tactic.buyThreshold")
				+ " tradeUsdThreshold=" + simProperties.getDoubleProperty("tactic.tradeUsdThreshold")
				+ " tradeScaleFactor=" + simProperties.getDoubleProperty("tactic.tradeScaleFactor"));
			log.info("Holding period return:\t" + result.holdingPeriodReturn);
			log.info("Daily avg trades:     \t" + result.dailyAvgTradeCount);
			log.info("% days with trades:   \t" + result.pctDaysWithTrades);
			log.info("Daily avg return:     \t" + result.dailyAvgReturn);
			log.info("Daily volatility:     \t" + result.dailyVolatility);
			log.info("Sharpe Ratio:         \t" + result.sharpeRatio);
			log.info("Win %                 \t" + result.winPct);
			log.info("Loss %                \t" + result.lossPct);
			log.info("Win-Loss              \t" + result.winPct / result.lossPct);

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Map<Long,List<Double>> cacheForecasts(List<ConsolidatedSnapshot> consolidatedSnapshots,
		MCProperties simProperties)
		throws IOException
	{
		ConsolidatedHistory history = new ConsolidatedHistory(intervalizer);
		ForecastCalculator forecastCalculator = ForecastFactory.getCalculatorInstance(simProperties);
		SimTimeKeeper simTimeKeeper = new SimTimeKeeper();
		ForecastAppender forecastAppender = null;
		if(simProperties.getBooleanProperty("sim.logForecasts", false)) {
			List<Path> existingFiles = Files.find(dataDir, 1,
				(path, attr) -> path.getFileName().toString().matches("forecasts-\\d{4}-\\d{2}-\\d{2}.csv"))
				.sorted()
				.collect(Collectors.toList());
			for(Path file : existingFiles) { Files.deleteIfExists(file); }
			forecastAppender = new ForecastAppender(dataDir, forecastFile, simTimeKeeper);
		}
		Map<Long,List<Double>> cache = new HashMap<>(365);
		for(ConsolidatedSnapshot snapshot : consolidatedSnapshots) {
			simTimeKeeper.advanceTime(snapshot.getTimeNanos());
			history.add(snapshot);
			List<Double> forecasts = new ArrayList<>(Product.count);
			for(Product product : Product.FAST_VALUES) {
				forecasts.add(product.getIndex(), forecastCalculator.calculate(history, product, forecastAppender));
			}
			cache.put(snapshot.getTimeNanos(), forecasts);
		}
		if(forecastAppender != null)
			forecastAppender.close();
		return cache;
	}

	private SimResult simulate(List<ConsolidatedSnapshot> consolidatedSnapshots, Map<Long,List<Double>> forecastCache,
		MCProperties simProperties)
		throws IOException
	{
		final boolean shouldLogDecisions = simProperties.getBooleanProperty("sim.logDecisions", false);
		final boolean shouldSkipJan = simProperties.getBooleanProperty("sim.skipJan", true);
		try {
			if(shouldLogDecisions) {
				Files.deleteIfExists(dataDir.resolve(decisionFile));
				Files.deleteIfExists(dataDir.resolve(dailyFile));
			}
		} catch(IOException e) {
			log.error("Could not delete existing file", e);
			throw new RuntimeException(e);
		}

		ConsolidatedHistory history = new ConsolidatedHistory(intervalizer);
		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		SimOrderBookManager orderBookManager = new SimOrderBookManager(timeKeeper);
		PositionsProvider positionsProvider = new EmptyPositionsProvider(startingUsd);
		Accountant accountant = new Accountant(positionsProvider);
		ForecastCalculator forecastCalculator = new CachedForecastCalculator(forecastCache);
		Tactic tactic = new TwoHourTactic(simProperties, timeKeeper, accountant);
		TradeRiskValidator tradeRiskValidator =
			new TradeRiskValidator(simProperties, timeKeeper, accountant, orderBookManager);
		SimExecutionEngine executionEngine = new SimExecutionEngine(simProperties, accountant, tactic, history);
		DecisionAppender decisionAppender = null;
		DailyAppender dailyAppender = null;
		if(shouldLogDecisions) {
			decisionAppender = new DecisionAppender(accountant, timeKeeper, dataDir, decisionFile);
			decisionAppender.open();
			dailyAppender = new DailyAppender(accountant, timeKeeper, intervalizer, dataDir, dailyFile);
			dailyAppender.open();
		}
		TradeEvaluator tradeEvaluator = new TradeEvaluator(history, forecastCalculator, tactic, tradeRiskValidator,
			executionEngine, decisionAppender, dailyAppender, null);
		SampleTesting sampleTesting = new SampleTesting(simProperties);

		long nextDay = intervalizer.calcNextDayMillis(0);
		List<Double> dailyPositionValuesUsd = new ArrayList<>(365);
		List<Integer> dailyTradeCount = new ArrayList<>(365);
		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedSnapshots) {
			if(shouldSkipJan && consolidatedSnapshot.getTimeNanos() < 1518048000 * 1000000000L)
				continue; // spotty/strange data before 2/8, just skip
			if(!sampleTesting.isSampleValid(consolidatedSnapshot.getTimeNanos()))
				continue; // allow in/out sample testing

			timeKeeper.advanceTime(consolidatedSnapshot.getTimeNanos());
			orderBookManager.update(consolidatedSnapshot);
			if(timeKeeper.epochMs() >= nextDay) {
				nextDay = intervalizer.calcNextDayMillis(timeKeeper.epochMs());
				dailyPositionValuesUsd.add(accountant.getPositionValueUsd(consolidatedSnapshot));
				dailyTradeCount.add(executionEngine.getAndResetTradeCount());
			}
			history.add(consolidatedSnapshot);
			tradeEvaluator.evaluate();
		}
		ConsolidatedSnapshot lastSnapshot = consolidatedSnapshots.get(consolidatedSnapshots.size() - 1);
		timeKeeper.advanceTime(nextDay * 1000000L);
		dailyPositionValuesUsd.add(accountant.getPositionValueUsd(lastSnapshot));
		dailyTradeCount.add(executionEngine.getAndResetTradeCount());
		if(dailyAppender != null)
			dailyAppender.writeDaily(lastSnapshot, null);

		log.debug("Ending positions USD " + accountant.getAvailable(Currency.USD)
			+ " BTC " + accountant.getAvailable(Currency.BTC)
			+ " BCH " + accountant.getAvailable(Currency.BCH)
			+ " ETH " + accountant.getAvailable(Currency.ETH)
			+ " LTC " + accountant.getAvailable(Currency.LTC));
		log.debug("Total ending position " + accountant.getPositionValueUsd(lastSnapshot));

		if(decisionAppender != null) decisionAppender.close();
		if(dailyAppender != null) dailyAppender.close();

		SimResult simResult = new SimResult(searchObjective, dailyPositionValuesUsd, dailyTradeCount);
		log.info("Simulated completed return " + simResult.holdingPeriodReturn + " sharpe " + simResult.sharpeRatio +
			" win " + simResult.winPct + " winloss " + simResult.winPct / simResult.lossPct);
		return simResult;
	}

	class CachedForecastCalculator implements ForecastCalculator {
		private Map<Long,List<Double>> cache;

		CachedForecastCalculator(Map<Long,List<Double>> cache) {
			this.cache = cache;
		}

		@Override
		public double calculate(final ConsolidatedHistory consolidatedHistory, final Product product,
			final ForecastAppender forecastAppender)
		{
			return cache.get(consolidatedHistory.latest().getTimeNanos()).get(product.getIndex());
		}

		@Override
		public Map<String,Double> getInputVariables(final ConsolidatedHistory consolidatedHistory,
			final Product product)
		{
			throw new UnsupportedOperationException();
		}
	}

	class SimOrderBook implements BBOProvider {
		private ProductSnapshot latest;

		void update(ProductSnapshot snapshot) {
			latest = snapshot;
		}

		@Override
		public void recordBBO(final BBO bbo) {
			bbo.bidPrice = latest.bidPrice;
			bbo.askPrice = latest.askPrice;
			bbo.bidSize = latest.bidSize;
			bbo.askSize = latest.askSize;
		}
	}

	class SimOrderBookManager extends OrderBookManager {
		SimOrderBook[] simOrderBooks;

		public SimOrderBookManager(final TimeKeeper timeKeeper) {
			super(timeKeeper);
			simOrderBooks = new SimOrderBook[Product.count];
			for(int i = 0; i < simOrderBooks.length; i++)
				simOrderBooks[i] = new SimOrderBook();
		}

		void update(ConsolidatedSnapshot snapshot) {
			for(Product product : Product.FAST_VALUES) {
				simOrderBooks[product.getIndex()].update(snapshot.getProductSnapshot(product));
			}
		}

		@Override
		public BBOProvider getBBOProvider(Product product) {
			return simOrderBooks[product.getIndex()];
		}
	}
}
