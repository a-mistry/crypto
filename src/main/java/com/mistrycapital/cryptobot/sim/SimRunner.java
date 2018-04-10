package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.common.Product;
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

import static com.mistrycapital.cryptobot.PeriodicEvaluator.INTERVAL_SECONDS;
import static com.mistrycapital.cryptobot.PeriodicEvaluator.SECONDS_TO_KEEP;

public class SimRunner implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final boolean LOG_FORECASTS = MCProperties.getBooleanProperty("sim.logForecasts", false);

	private final Path dataDir;
	private final SimTimeKeeper timeKeeper;
	private final ForecastAppender forecastAppender;
	private final double[] forecasts;
	private final ForecastCalculator forecastCalculator;

	public SimRunner(Path dataDir, SimTimeKeeper timeKeeper, ForecastAppender forecastAppender) {
		this.dataDir = dataDir;
		this.timeKeeper = timeKeeper;
		this.forecastAppender = forecastAppender;
		forecasts = new double[Product.count];
		forecastCalculator = new Snowbird();
	}

	@Override
	public void run() {
		try {

			// first read sampled data
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

			// now "sim"
			ConsolidatedHistory history = new ConsolidatedHistory(SECONDS_TO_KEEP / INTERVAL_SECONDS);
			SimTimeKeeper treatmentTimeKeeper = new SimTimeKeeper();
			for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedSnapshots) {
				treatmentTimeKeeper.advanceTime(consolidatedSnapshot.getTimeNanos());
				history.add(consolidatedSnapshot);
				evaluate(consolidatedSnapshot, history);
			}

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
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

	/**
	 * Evaluates forecasts and trades if warranted
	 */
	private void evaluate(ConsolidatedSnapshot snapshot, ConsolidatedHistory history) {
		// update signals
		for(Product product : Product.FAST_VALUES) {
			forecasts[product.getIndex()] = forecastCalculator.calculate(history, product);
		}
		if(LOG_FORECASTS) {
			try {
				forecastAppender.recordForecasts(timeKeeper.epochMs(), forecasts);
			} catch(IOException e) {
				log.error("Error saving forecast data", e);
			}
		}

		// possibly trade
	}
}
