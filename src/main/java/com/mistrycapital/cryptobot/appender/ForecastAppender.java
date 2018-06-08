package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ForecastAppender extends CommonFileAppender {
	private boolean addHeaderOnNextWrite;
	private List<String> headers;
	private final TimeKeeper timeKeeper;

	public ForecastAppender(final Path dataDir, final String filename, final TimeKeeper timeKeeper) {
		super(timeKeeper, dataDir, filename.replace(".csv", ""), ".csv", RollingPolicy.DAILY,
			FlushPolicy.FLUSH_EACH_WRITE);
		this.timeKeeper = timeKeeper;
	}

	public void logForecast(final Product product, final Map<String,Double> inputVariables, final double forecast)
		throws IOException
	{
		if(addHeaderOnNextWrite) {
			headers = inputVariables.keySet().stream().collect(Collectors.toList());
			writeHeaders();
			addHeaderOnNextWrite = false;
		}

		long timeMs = timeKeeper.epochMs();
		final ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC);
		final String formattedDateTime = dateTime.format(CSV_DATE_FORMATTER);
		final StringBuilder builder = new StringBuilder();
		builder.append(formattedDateTime);
		builder.append(',');
		builder.append(timeMs / 1000L);
		builder.append(',');
		builder.append(product);
		builder.append(',');
		for(final var entry : inputVariables.entrySet()) {
			builder.append(entry.getValue());
			builder.append(',');
		}
		builder.append(forecast);
		append(builder.toString());
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		if(headers == null)
			addHeaderOnNextWrite = true;
		else
			writeHeaders();
	}

	private void writeHeaders()
		throws IOException
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("date,unixTimestamp,product,");
		for(String name : headers) {
			builder.append(name);
			builder.append(',');
		}
		builder.append("forecast");
		append(builder.toString());
	}
}
