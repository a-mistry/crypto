package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Stores a daily csv file with interval data
 */
public class IntervalDataAppender extends CommonFileAppender {
	public IntervalDataAppender(final Path dataDir, final String baseFilename, final TimeKeeper timeKeeper) {
		super(timeKeeper, dataDir, baseFilename, ".csv", RollingPolicy.DAILY, FlushPolicy.FLUSH_EACH_WRITE);
	}

	private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** Records aggregated data for the latest interval and static data, with the given timestamp */
	public void recordSnapshot(final long timeMicros, final ConsolidatedData consolidatedData)
		throws IOException
	{
		final ZonedDateTime dateTime =
			ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMicros / 1000L), ZoneOffset.UTC);
		final String formattedDateTime = dateTime.format(dateTimeFormatter);
		for(Product product : Product.FAST_VALUES) {
			final StringBuilder builder = new StringBuilder();
			builder.append(formattedDateTime);
			builder.append(',');
			builder.append(timeMicros / 1000000L);
			builder.append(',');
			builder.append(product);
			builder.append(',');
			builder.append(consolidatedData.toCSVString(product));
			append(builder.toString());
		}
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		append("date,unix_timestamp,product," + IntervalData.csvHeaderRow());
	}
}