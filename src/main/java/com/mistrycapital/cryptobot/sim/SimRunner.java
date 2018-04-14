package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.accounting.EmptyPositionsProvider;
import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.forecasts.Solitude;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.tactic.Tactic;
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
import java.util.List;
import java.util.stream.Collectors;

public class SimRunner implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Path dataDir;
	private final SimTimeKeeper timeKeeper;
	private final Intervalizer intervalizer;
	private final ForecastAppender forecastAppender;
	private final double[] forecasts;
	private final ForecastCalculator forecastCalculator;
	private final boolean shouldLogForecasts;
	private final int startingUsd;

	public SimRunner(MCProperties properties, Intervalizer intervalizer, Path dataDir, SimTimeKeeper timeKeeper,
		ForecastAppender forecastAppender)
	{
		this.dataDir = dataDir;
		this.timeKeeper = timeKeeper;
		this.intervalizer = intervalizer;
		this.forecastAppender = forecastAppender;
		forecasts = new double[Product.count];
		forecastCalculator = new Solitude(properties);
		shouldLogForecasts = properties.getBooleanProperty("sim.logForecasts", false);
		startingUsd = properties.getIntProperty("sim.startUsd", 10000);
	}

	@Override
	public void run() {
		try {

			List<ConsolidatedSnapshot> consolidatedSnapshots = readMarketData();

			long startNanos = System.nanoTime();
			simulate(consolidatedSnapshots);
			log.debug("Simulation treatment ended in " + ((System.nanoTime() - startNanos) / 1000000.0) + "ms");

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<ConsolidatedSnapshot> readMarketData()
		throws IOException
	{
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

	private void simulate(List<ConsolidatedSnapshot> consolidatedSnapshots) {
		ConsolidatedHistory history = new ConsolidatedHistory(intervalizer);
		SimTimeKeeper treatmentTimeKeeper = new SimTimeKeeper();
		PositionsProvider positionsProvider = new EmptyPositionsProvider(startingUsd);
		Accountant accountant = new Accountant(positionsProvider);
		MCProperties simProperties = new MCProperties();
		Tactic tactic = new Tactic(simProperties, accountant);
		SimExecutionEngine executionEngine = new SimExecutionEngine(simProperties, accountant);

		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedSnapshots) {
			treatmentTimeKeeper.advanceTime(consolidatedSnapshot.getTimeNanos());
			history.add(consolidatedSnapshot);
			executionEngine.useSnapshot(consolidatedSnapshot);
			evaluate(consolidatedSnapshot, history, tactic, executionEngine);
		}
		ConsolidatedSnapshot lastSnapshot = consolidatedSnapshots.get(consolidatedSnapshots.size() - 1);
		log.debug("Ending positions USD " + accountant.getAvailable(Currency.USD)
			+ " BTC " + accountant.getAvailable(Currency.BTC)
			+ " BCH " + accountant.getAvailable(Currency.BCH)
			+ " ETH " + accountant.getAvailable(Currency.ETH)
			+ " LTC " + accountant.getAvailable(Currency.LTC));
		log.debug("Total ending position " + accountant.getPositionValueUsd(lastSnapshot));
	}

	// TODO: merge this code with PeriodicEvaluator

	/**
	 * Evaluates forecasts and trades if warranted
	 */
	private void evaluate(ConsolidatedSnapshot snapshot, ConsolidatedHistory history, Tactic tactic,
		ExecutionEngine executionEngine)
	{
		// update signals
		for(Product product : Product.FAST_VALUES) {
			forecasts[product.getIndex()] = forecastCalculator.calculate(history, product);
		}
		if(shouldLogForecasts) {
			try {
				forecastAppender.recordForecasts(timeKeeper.epochMs(), forecasts);
			} catch(IOException e) {
				log.error("Error saving forecast data", e);
			}
		}

		// possibly trade
		List<TradeInstruction> instructions = tactic.decideTrades(snapshot, forecasts);
		if(instructions != null && instructions.size() > 0)
			executionEngine.trade(instructions);

		// TODO: record positions and trades in decision log
	}
}
