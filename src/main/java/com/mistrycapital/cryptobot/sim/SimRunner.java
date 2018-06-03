package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.accounting.EmptyPositionsProvider;
import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.DailyAppender;
import com.mistrycapital.cryptobot.appender.DecisionAppender;
import com.mistrycapital.cryptobot.forecasts.*;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.tactic.TradeEvaluator;
import com.mistrycapital.cryptobot.tactic.TwoHourTactic;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimRunner implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Intervalizer intervalizer;
	private final Path dataDir;
	private final double[] forecasts;
	private final String decisionFile;
	private final String dailyFile;
	private final String searchFile;
	private final Path forecastCalcFile;
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
		forecastCalcFile = dataDir.resolve(properties.getProperty("sim.forecastCalcFile", "forecast-calcs.csv"));
		startingUsd = properties.getIntProperty("sim.startUsd", 10000);
		parameterOptimizer = new ParameterOptimizer();
		searchObjective = properties.getProperty("sim.searchObjective", "return");
	}

	@Override
	public void run() {
		try {

			List<ConsolidatedSnapshot> consolidatedSnapshots =
				SnapshotReader.readSnapshots(SnapshotReader.getSampleFiles(dataDir));
			MCProperties simProperties = new MCProperties();

			boolean search = simProperties.getBooleanProperty("sim.searchParameters", false);
			if(search) {
				simProperties.put("sim.logDecisions", "false");
				simProperties.put("sim.logForecastCalc", "false");
				simProperties.put("tactic.buyForecastProportion", "false");
				List<ParameterSearchSpace> searchList = Arrays.asList(
					new ParameterSearchSpace("tactic.buyThreshold", 0.005, 0.01, 21),
//					new ParameterSearchSpace("tactic.buyUpperThreshold", 0.01, 0.02, 6),
					new ParameterSearchSpace("tactic.tradeScaleFactor", 1.0, 10.0, 10)
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
								SimResult result = simulate(consolidatedSnapshots, properties);
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
			long startNanos = System.nanoTime();
			SimResult result = simulate(consolidatedSnapshots, simProperties);
			log.info("Simulation treatment ended in " + ((System.nanoTime() - startNanos) / 1000000.0) + "ms");
			log.info("Tactic buyThreshold=" + simProperties.getDoubleProperty("tactic.buyThreshold")
				+ " tradeUsdThreshold=" + simProperties.getDoubleProperty("tactic.tradeUsdThreshold")
				+ " tradeScaleFactor=" + simProperties.getDoubleProperty("tactic.tradeScaleFactor"));
			log.info("Holding period return:\t" + result.holdingPeriodReturn);
			log.info("Daily avg trades:     \t" + result.dailyAvgTradeCount);
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

	private SimResult simulate(List<ConsolidatedSnapshot> consolidatedSnapshots, MCProperties simProperties)
		throws IOException
	{
		final boolean shouldLogDecisions = simProperties.getBooleanProperty("sim.logDecisions", false);
		final boolean shouldLogForecastCalc = simProperties.getBooleanProperty("sim.logForecastCalc", false);
		final boolean shouldSkipJan = simProperties.getBooleanProperty("sim.skipJan", true);
		try {
			if(shouldLogDecisions) {
				Files.deleteIfExists(dataDir.resolve(decisionFile));
				Files.deleteIfExists(dataDir.resolve(dailyFile));
			}
			if(shouldLogForecastCalc)
				Files.deleteIfExists(forecastCalcFile);
		} catch(IOException e) {
			log.error("Could not delete existing file", e);
			throw new RuntimeException(e);
		}

		ConsolidatedHistory history = new ConsolidatedHistory(intervalizer);
		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		PositionsProvider positionsProvider = new EmptyPositionsProvider(startingUsd);
		Accountant accountant = new Accountant(positionsProvider);
		ForecastCalculator forecastCalculator = new Snowbird(simProperties);
		Tactic tactic = new TwoHourTactic(simProperties, timeKeeper, accountant);
		TradeRiskValidator tradeRiskValidator = new TradeRiskValidator(simProperties, timeKeeper, accountant);
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
			executionEngine, decisionAppender, dailyAppender);

		// We can speed this up by optimizing the following
		// 1. snowbird getInputVariables() calculation
		// 2. snowbird - use a more efficient map or eliminate the map
		// 3. ConsolidatedHistory add is slow because it is O(n), use a circular buffer
		long nextDay = intervalizer.calcNextDayMillis(0);
		List<Double> dailyPositionValuesUsd = new ArrayList<>(365);
		List<Integer> dailyTradeCount = new ArrayList<>(365);
		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedSnapshots) {
			if(shouldSkipJan && consolidatedSnapshot.getTimeNanos() < 1518048000 * 1000000000L)
				continue; // spotty/strange data before 2/8, just skip

			timeKeeper.advanceTime(consolidatedSnapshot.getTimeNanos());
			if(timeKeeper.epochMs() >= nextDay) {
				nextDay = intervalizer.calcNextDayMillis(timeKeeper.epochMs());
				dailyPositionValuesUsd.add(accountant.getPositionValueUsd(consolidatedSnapshot));
				dailyTradeCount.add(executionEngine.getAndResetTradeCount());
			}
			history.add(consolidatedSnapshot);
			tradeEvaluator.evaluate();
		}

		ConsolidatedSnapshot lastSnapshot = consolidatedSnapshots.get(consolidatedSnapshots.size() - 1);
		dailyPositionValuesUsd.add(accountant.getPositionValueUsd(lastSnapshot));

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
}
