package com.mistrycapital.cryptobot.regression;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.sim.SnapshotReader;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates datasets for regression analysis
 */
public class DatasetGenerator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final MCProperties properties;
	private final Path dataDir;
	private final ForecastCalculator forecastCalculator;

	public DatasetGenerator(MCProperties properties, Path dataDir, ForecastCalculator forecastCalculator) {
		this.properties = properties;
		this.dataDir = dataDir;
		this.forecastCalculator = forecastCalculator;
	}

	/**
	 * Reads sample data and calculates forecast inputs
	 *
	 * @return Table of forecast inputs at each point
	 */
	public Table<TimeProduct> getForecastDataset()
		throws IOException
	{
		final List<ConsolidatedSnapshot> snapshots =
			SnapshotReader.readSnapshots(SnapshotReader.getSampleFiles(dataDir));

		var intervalizer = new Intervalizer(properties);
		var consolidatedHistory = new ConsolidatedHistory(intervalizer);

		var columnMap = new HashMap<String,Column<TimeProduct>>();
		for(ConsolidatedSnapshot snapshot : snapshots) {
			consolidatedHistory.add(snapshot);
			for(Product product : Product.FAST_VALUES) {
				var inputVariables = forecastCalculator.getInputVariables(consolidatedHistory, product);

				var key = new TimeProduct(snapshot.getTimeNanos(), product);
				for(var entry : inputVariables.entrySet()) {
					var column = columnMap.computeIfAbsent(entry.getKey(), k -> new Column<>());
					column.add(key, entry.getValue());
				}
			}
		}

		var table = new Table<TimeProduct>();
		try {
			for(var entry : columnMap.entrySet())
				table.add(entry.getKey(), entry.getValue());
		} catch(DuplicateColumnException e) {
			throw new RuntimeException(e);
		}
		return table;
	}

	/**
	 * Reads return dataset from gdax files
	 *
	 * @return Table of future returns
	 */
	public Table<TimeProduct> getReturnDataset()
		throws IOException
	{
		var table = new Table<TimeProduct>();
		try {
			table.add("fut_ret_2h", new Column<TimeProduct>());
		} catch(DuplicateColumnException e) {
			throw new RuntimeException(e);
		}

		// TODO: Convert to a generic CSV to Table reader
		for(Path returnFile : getReturnFiles()) {

			try(
				Reader in = Files.newBufferedReader(returnFile);
				CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
			) {
				// read each row and add to columns
				for(CSVRecord record : parser) {
					var timeNanos = Long.parseLong(record.get("unix_timestamp")) * 1000000L;
					var product = Product.parse(record.get("product"));
					var key = new TimeProduct(timeNanos, product);
					for(String columnName : Arrays.asList(
						"fut_ret_2h"
					)) {
						String value = record.get(columnName);
						if(value != null && !value.isEmpty())
							table.column(columnName).add(key, Double.parseDouble(value));
					}
				}
			}
		}

		return table;
	}

	private List<Path> getReturnFiles()
		throws IOException
	{
		return Files
			.find(dataDir, 1,
				(path, attr) -> path.getFileName().toString().matches("gdax-returns-[A-Z]{3}-USD.csv"))
			.collect(Collectors.toList());
	}
}
