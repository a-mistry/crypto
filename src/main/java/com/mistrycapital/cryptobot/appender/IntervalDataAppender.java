package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Stores a daily csv file with interval data
 */
public class IntervalDataAppender extends CommonFileAppender {

	public IntervalDataAppender(final Path dataDir, final String baseFilename, final TimeKeeper timeKeeper) {
		super(timeKeeper, dataDir, baseFilename, ".csv", RollingPolicy.DAILY, FlushPolicy.FLUSH_EACH_WRITE);
	}

	/** Records aggregated data for the latest interval and static data, with the given timestamp */
	public void recordSnapshot(final long timeMs, final ConsolidatedSnapshot consolidatedSnapshot)
		throws IOException
	{
		final ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC);
		final String formattedDateTime = dateTime.format(CSV_DATE_FORMATTER);
		for(Product product : Product.FAST_VALUES) {
			final StringBuilder builder = new StringBuilder();
			builder.append(formattedDateTime);
			builder.append(',');
			builder.append(timeMs / 1000L);
			builder.append(',');
			builder.append(consolidatedSnapshot.getProductSnapshot(product).toCSVString());
			append(builder.toString());
		}
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		append("date,unix_timestamp," + ProductSnapshot.csvHeaderRow());
	}
}