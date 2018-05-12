package com.mistrycapital.cryptobot.regression;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.sim.SnapshotReader;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.util.MCProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Creates datasets for regression analysis
 */
public class DatasetGenerator {
	private final Path dataDir;
	private final ForecastCalculator forecastCalculator;

	public DatasetGenerator(Path dataDir, ForecastCalculator forecastCalculator) {
		this.dataDir = dataDir;
		this.forecastCalculator = forecastCalculator;
	}

	public void generate() {
		try {
			List<ConsolidatedSnapshot> snapshots = SnapshotReader.readSnapshots(SnapshotReader.getSampleFiles(dataDir));
			MCProperties properties = new MCProperties();
			Intervalizer intervalizer = new Intervalizer(properties);
			ConsolidatedHistory consolidatedHistory = new ConsolidatedHistory(intervalizer);
			for(ConsolidatedSnapshot snapshot : snapshots) {
				consolidatedHistory.add(snapshot);
				for(Product product : Product.FAST_VALUES) {
					double[] inputVariables = forecastCalculator.getInputVariables(consolidatedHistory, product);
				}
			}
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
}
