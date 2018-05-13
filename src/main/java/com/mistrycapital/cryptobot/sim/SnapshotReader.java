package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to read historical snapshots from files
 */
public class SnapshotReader {
	private static final Logger log = MCLoggerFactory.getLogger();

	/**
	 * @return List of all sampled interval files in the data dir
	 */
	public static List<Path> getSampleFiles(Path dataDir)
		throws IOException
	{
		return Files
			.find(dataDir, 1,
				(path, attr) -> path.getFileName().toString().matches("samples-\\d{4}-\\d{2}-\\d{2}.csv"))
			.sorted()
			.collect(Collectors.toList());
	}

	/**
	 * Reads given sample files in order and creates a list of snapshots
	 * @return Snapshots read from the files in order
	 */
	public static List<ConsolidatedSnapshot> readSnapshots(Collection<Path> sampleFiles)
		throws IOException
	{
		List<ConsolidatedSnapshot> consolidatedSnapshots = new ArrayList<>(365 * 24 * 12); // 1 year
		ProductSnapshot[] productSnapshots = new ProductSnapshot[Product.count];
		SimTimeKeeper timeKeeper = new SimTimeKeeper();

		// TODO: Improve performance by using a faster CSV reader
		for(Path sampleFile : sampleFiles) {
			try(
				Reader in = Files.newBufferedReader(sampleFile);
				CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);
			) {
				for(CSVRecord record : parser) {
					long timeNanos = Long.parseLong(record.get("unixTimestamp")) * 1000000000L;
					timeKeeper.advanceTime(timeNanos);
					log.debug("Restoring snapshot from " + timeKeeper.iso8601());
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

	/**
	 * @return true if array is filled with values, false if one is null
	 */
	private static <T> boolean isFull(T[] array) {
		for(T element : array)
			if(element == null)
				return false;

		return true;
	}
}
