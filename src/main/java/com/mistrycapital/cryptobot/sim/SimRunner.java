package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.accounting.EmptyPositionsProvider;
import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.appender.DailyAppender;
import com.mistrycapital.cryptobot.appender.DecisionAppender;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.forecasts.*;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.tactic.TradeEvaluator;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SimRunner implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Intervalizer intervalizer;
	private final Path dataDir;
	private final double[] forecasts;
	private final String decisionFile;
	private final String dailyFile;
	private final Path forecastCalcFile;
	private final int startingUsd;
	private final ParameterOptimizer parameterOptimizer;

	public SimRunner(MCProperties properties, Path dataDir)
	{
		this.dataDir = dataDir;
		intervalizer = new Intervalizer(properties);
		forecasts = new double[Product.count];
		decisionFile = properties.getProperty("sim.decisionFile", "decisions.csv");
		dailyFile = properties.getProperty("sim.dailyFile", "daily.csv");
		forecastCalcFile = dataDir.resolve(properties.getProperty("sim.forecastCalcFile", "forecast-calcs.csv"));
		startingUsd = properties.getIntProperty("sim.startUsd", 10000);
		parameterOptimizer = new ParameterOptimizer(properties.getIntProperty("sim.ladderPoints", 10));
	}

	@Override
	public void run() {
		try {

			List<ConsolidatedSnapshot> consolidatedSnapshots = readMarketData();
			MCProperties simProperties = new MCProperties();

			boolean search = simProperties.getBooleanProperty("sim.searchParameters", false);
			if(search) {
				// ladder on in/out thresholds
				simProperties.put("sim.logDecisions", "false");
				simProperties.put("sim.logForecastCalc", "false");
				SimResult result = parameterOptimizer.optimize(simProperties,
					Arrays.asList(
						new ParameterSearchSpace("tactic.inThreshold.default", 0.0, 0.06),
						new ParameterSearchSpace("tactic.outThreshold.default", -0.06, 0.0)
					),
					properties -> {
						try {
							return simulate(consolidatedSnapshots, properties);
						} catch(IOException e) {
							log.error("IO error running simulation", e);
							throw new RuntimeException(e);
						}
					}
				);
				log.info("Max return of " + result.holdingPeriodReturn + " sharpe of " + result.sharpeRatio
					+ " achieved at"
					+ " in=" + simProperties.getDoubleProperty("tactic.inThreshold.default")
					+ " out=" + simProperties.getDoubleProperty("tactic.outThreshold.default"));
			}
			simProperties.put("sim.logDecisions", "true");
			long startNanos = System.nanoTime();
			SimResult result = simulate(consolidatedSnapshots, simProperties);
			log.info("Simulation treatment ended in " + ((System.nanoTime() - startNanos) / 1000000.0) + "ms");
			log.info("Tactic in=" + simProperties.getDoubleProperty("tactic.inThreshold.default")
				+ " out=" + simProperties.getDoubleProperty("tactic.outThreshold.default"));
			log.info("Holding period return:\t" + result.holdingPeriodReturn);
			log.info("Daily avg return:     \t" + result.dailyAvgReturn);
			log.info("Daily volatility:     \t" + result.dailyVolatility);
			log.info("Sharpe Ratio:         \t" + result.sharpeRatio);
			log.info("Win %                 \t" + result.winPct);

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<ConsolidatedSnapshot> readMarketData()
		throws IOException
	{
		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		List<ConsolidatedSnapshot> consolidatedSnapshots = new ArrayList<>(365 * 24 * 12); // 1 year
		ProductSnapshot[] productSnapshots = new ProductSnapshot[Product.count];

		for(Path sampleFile : getSampleFiles()) {
			try(
				Reader in = Files.newBufferedReader(sampleFile);
				CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
			) {
				for(CSVRecord record : parser) {
					long timeNanos = Long.parseLong(record.get("unixTimestamp")) * 1000000000L;
					timeKeeper.advanceTime(timeNanos);
					ProductSnapshot productSnapshot = new ProductSnapshot(record);
					productSnapshots[productSnapshot.product.getIndex()] = productSnapshot;

					if(isFull(productSnapshots)) {
						ConsolidatedSnapshot consolidatedSnapshot =
							new ConsolidatedSnapshot(productSnapshots, timeNanos);
						consolidatedSnapshots.add(consolidatedSnapshot);
						productSnapshots = new ProductSnapshot[Product.count];
					}
				}
			}
		}

		return consolidatedSnapshots;
	}

	private List<Path> getSampleFiles()
		throws IOException
	{
		return Files
			.find(dataDir, 1,
				(path, attr) -> path.getFileName().toString().matches("samples-\\d{4}-\\d{2}-\\d{2}.csv"))
			.sorted()
			.collect(Collectors.toList());
	}

	/**
	 * @return true if array is filled with values, false if one is null
	 */
	private boolean isFull(ProductSnapshot[] snapshots) {
		for(ProductSnapshot snapshot : snapshots)
			if(snapshot == null)
				return false;

		return true;
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
		ForecastCalculationLogger calculationLogger = null;
		if(shouldLogForecastCalc) {
			calculationLogger = new ForecastCalculationLogger(timeKeeper, forecastCalcFile);
			calculationLogger.open();
		}
		ForecastCalculator forecastCalculator = new Brighton(simProperties);
		Tactic tactic = new Tactic(simProperties, accountant);
		TradeRiskValidator tradeRiskValidator = new TradeRiskValidator(simProperties, timeKeeper, accountant);
		ExecutionEngine executionEngine = new SimExecutionEngine(simProperties, accountant, history);
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

		long nextDay = intervalizer.calcNextDayMillis(0);
		List<Double> dailyPositionValuesUsd = new ArrayList<>(365);
		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedSnapshots) {
			if(shouldSkipJan && consolidatedSnapshot.getTimeNanos() < 1517448021000000000L)
				continue; // spotty data before 2/1, just skip

			timeKeeper.advanceTime(consolidatedSnapshot.getTimeNanos());
			if(timeKeeper.epochMs() >= nextDay) {
				nextDay = intervalizer.calcNextDayMillis(timeKeeper.epochMs());
				dailyPositionValuesUsd.add(accountant.getPositionValueUsd(consolidatedSnapshot));
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
		if(calculationLogger != null) calculationLogger.close();

		return new SimResult(dailyPositionValuesUsd);
	}

	class SimResult implements Comparable<SimResult> {
		final List<Double> dailyPositionValuesUsd;
		final double holdingPeriodReturn;
		final double dailyAvgReturn;
		final double dailyVolatility;
		final double sharpeRatio;
		final double winPct;

		SimResult(List<Double> dailyPositionValuesUsd) {
			this.dailyPositionValuesUsd = dailyPositionValuesUsd;
			holdingPeriodReturn =
				dailyPositionValuesUsd.get(dailyPositionValuesUsd.size() - 1) / dailyPositionValuesUsd.get(0) - 1;

			final double[] dailyReturns = new double[dailyPositionValuesUsd.size() - 1];
			for(int i = 0; i < dailyReturns.length; i++)
				dailyReturns[i] = dailyPositionValuesUsd.get(i + 1) / dailyPositionValuesUsd.get(i) - 1;
			double dailySum = 0.0;
			for(final double val : dailyReturns)
				dailySum += val;
			dailyAvgReturn = dailySum / dailyReturns.length;

			dailySum = 0.0;
			for(final double val : dailyReturns) {
				final double diff = val - dailyAvgReturn;
				dailySum += diff * diff;
			}
			dailyVolatility = Math.sqrt(dailySum / dailyReturns.length);

			sharpeRatio = dailyVolatility != 0.0 ? dailyAvgReturn / dailyVolatility : 0.0;

			int winCount = 0;
			for(final double val : dailyReturns)
				if(val >= 0.0)
					winCount++;
			winPct = ((double) winCount) / dailyReturns.length;
		}

		/**
		 * This is our objective function
		 */
		public int compareTo(SimResult b) {
//			return Double.compare(holdingPeriodReturn, b.holdingPeriodReturn);
			return Double.compare(sharpeRatio, b.sharpeRatio);
//			return Double.compare(winPct, b.winPct);
		}
	}
}
