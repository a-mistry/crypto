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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates datasets for regression analysis
 */
public class DatasetGenerator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final MCProperties properties;

	public DatasetGenerator(MCProperties properties) {
		this.properties = properties;
	}

	/**
	 * Reads sample data and calculates forecast inputs
	 *
	 * @return Table of forecast inputs at each point
	 */
	public Table<TimeProduct> calcSignalDataset(Path dataDir, ForecastCalculator forecastCalculator)
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
			addReturns(table);
		} catch(DuplicateColumnException e) {
			throw new RuntimeException(e);
		}
		return table;
	}

	/**
	 * Add fut_ret_2h column based on lastPrice column in input data
	 */
	private void addReturns(Table<TimeProduct> inputData)
		throws DuplicateColumnException
	{
		Column<TimeProduct> retCol = new Column<>();
		final long twoHourNanos = 2 * 60 * 60 * 1000000000L;
		final long threeHourNanos = 3 * 60 * 60 * 1000000000L;

		for(Product product : Product.FAST_VALUES) {

			Table<TimeProduct> productTable = inputData.filter(key -> key.product == product);

			Iterator<Row<TimeProduct>> futIter = productTable.iterator();
			if(!futIter.hasNext()) continue; // no data
			var futRow = futIter.next();

			var futPrice = Double.NaN;

			for(Row<TimeProduct> row : productTable) {

				// Need to find the price just after row + 2h
				final var rowNanos = row.getKey().timeInNanos;
				final var plus2hNanos = rowNanos + twoHourNanos;
				while(futRow.getKey().timeInNanos <= plus2hNanos && futIter.hasNext()) {
					final var lastPriceCol = futRow.getColumn("lastPrice");
					if(!Double.isNaN(lastPriceCol))
						futPrice = lastPriceCol;
					futRow = futIter.next();
				}

				final long futNanos = futRow.getKey().timeInNanos;
				if(futNanos <= plus2hNanos)
					break; // no more data
				if(futNanos > rowNanos + threeHourNanos)
					continue; // don't go too far into future

				final var curPrice = row.getColumn("lastPrice");
				final var ret = futPrice / curPrice - 1.0;
				if(!Double.isNaN(ret))
					retCol.add(row.getKey(), ret);
			}

		}
		inputData.add("fut_ret_2h", retCol);
	}

	// TODO: Convert this into a reader that reads all the cached forecast variable data

	/**
	 * Reads table from csv file
	 *
	 * @return Table of future returns
	 */
	public Table<TimeProduct> readCSVDataset(Path dataFile)
		throws IOException
	{
		final var table = new Table<TimeProduct>();

		try(
			Reader in = Files.newBufferedReader(dataFile);
			CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
		)
		{
			Map<String,Integer> headerMap = parser.getHeaderMap();
			List<String> columnNames = new ArrayList<>(headerMap.keySet());
			columnNames.remove("unixTimestamp");
			columnNames.remove("product");
			List<Column<TimeProduct>> columns = columnNames.stream()
				.map(k -> new Column<TimeProduct>())
				.collect(Collectors.toList());
			List<Integer> columnIndices = columnNames.stream()
				.map(headerMap::get)
				.collect(Collectors.toList());

			// read each row and add to columns
			for(CSVRecord record : parser) {
				var timeNanos = Long.parseLong(record.get("unixTimestamp")) * 1000000000L;
				var product = Product.parse(record.get("product"));
				var key = new TimeProduct(timeNanos, product);
				for(int i = 0; i < columnNames.size(); i++) {
					String value = record.get(columnIndices.get(i));
					if(value != null && !value.isEmpty() && !value.equals("NaN"))
						columns.get(i).add(key, Double.parseDouble(value));
				}
			}

			for(int i = 0; i < columnNames.size(); i++)
				table.add(columnNames.get(i), columns.get(i));

		} catch(DuplicateColumnException e) {
			throw new RuntimeException(e);
		}

		return table;
	}

	public void writeOutDataset(Table<TimeProduct> data, Path dataFile)
		throws IOException
	{
		try(
			BufferedWriter out = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8);
		)
		{
			boolean first = true;
			for(Row<TimeProduct> row : data) {
				if(first) {
					out.append("unixTimestamp,product," +
						Arrays.stream(row.getColumnNames()).collect(Collectors.joining(",")) + "\n");
					first = false;
				}

				out.append((row.getKey().timeInNanos / 1000000000L) + "," + row.getKey().product + "," +
					Arrays.stream(row.getColumnValues()).mapToObj(Double::toString)
						.collect(Collectors.joining(",")) +
					"\n");
			}
		}
	}
}
