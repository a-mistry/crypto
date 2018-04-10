package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

public class ForecastAppender extends CommonFileAppender {

	public ForecastAppender(Path dataDir, String baseFilename, TimeKeeper timeKeeper) {
		super(timeKeeper, dataDir, baseFilename, ".csv", RollingPolicy.DAILY, FlushPolicy.FLUSH_EACH_WRITE);
	}

	/**
	 * Saves forecasted returns to the file with the given timestamp
	 */
	public void recordForecasts(long timeMs, double[] forecastMap) throws IOException {
		final ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC);
		final String formattedDateTime = dateTime.format(CSV_DATE_FORMATTER);
		StringBuilder builder = new StringBuilder();
		builder.append(formattedDateTime);
		builder.append(',');
		builder.append(timeMs / 1000L);
		for(Product product : Product.FAST_VALUES) {
			builder.append(',');
			builder.append(forecastMap[product.getIndex()]);
		}
		append(builder.toString());
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		StringBuilder builder = new StringBuilder();
		builder.append("date,unix_timestamp");
		for(Product product : Product.FAST_VALUES) {
			builder.append(",fc_");
			builder.append(product.toString().substring(0, 3).toLowerCase(Locale.US));
		}
		append(builder.toString());
	}
}
