package com.mistrycapital.cryptobot.appender;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

/**
 * Appends messages to a log file, with a new file used every hour
 */
public class GdaxMessageAppender implements FileAppender {
	private final TimeKeeper timeKeeper;
	private final OrderBookManager orderBookManager;
	private final Path logDir;
	private final String baseFilename;
	private final String extension;
	private long nextHourMillis;
	private BufferedWriter writer;

	private GdaxMessageAppender(final Path logDir, final String baseFilename, String extension,
		final TimeKeeper timeKeeper, final OrderBookManager orderBookManager)
		throws IOException
	{
		if(extension.startsWith(".")) {
			extension = extension.substring(1, extension.length());
		}
		this.logDir = logDir;
		this.baseFilename = baseFilename;
		this.extension = extension;
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		openNewHourFile();
	}

	public static GdaxMessageAppender create(final Path logDir, final String baseFilename, final String extension,
		final TimeKeeper timeKeeper, final OrderBookManager orderBookManager)
		throws IOException
	{
		return new GdaxMessageAppender(logDir, baseFilename, extension, timeKeeper, orderBookManager);
	}

	/**
	 * Writes the message to the file. Appends a newline to each message
	 */
	public void append(final String msg)
		throws IOException
	{
		rollIfNeeded();

		writer.append(msg + '\n');
	}

	public void close()
		throws IOException
	{
		writer.close();
	}

	/**
	 * Rolls to the next hour file if needed
	 *
	 * @return true if we rolled to a new file
	 */
	private boolean rollIfNeeded()
		throws IOException
	{
		if(System.currentTimeMillis() < nextHourMillis) {
			return false;
		}

		openNewHourFile();
		for(Product product : Product.FAST_VALUES) {
			final OrderBook book = orderBookManager.getBook(product);
			if(book != null) {
				final String snapshot = book.getGdaxSnapshot();
				writer.append(snapshot + '\n');
			}
		}

		return true;
	}

	private void openNewHourFile()
		throws IOException
	{
		if(writer != null) {
			writer.close();
		}

		final long curMillis = System.currentTimeMillis();
		final Path logFile = logDir.resolve(getLogFileName(curMillis));
		writer = Files
			.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND,
				StandardOpenOption.WRITE);

		nextHourMillis = calcNextHourMillis(curMillis);
	}

	String getLogFileName(long timeInMillis) {
		final Instant instant = Instant.ofEpochMilli(timeInMillis);
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);

		StringBuilder builder = new StringBuilder();
		builder.append(baseFilename);
		builder.append('-');
		builder.append(
			String.format("%04d-%02d-%02d-%02d",
				dateTime.getYear(),
				dateTime.getMonth().getValue(),
				dateTime.getDayOfMonth(),
				dateTime.getHour())
		);
		builder.append('.');
		builder.append(extension);
		return builder.toString();
	}

	/**
	 * @param curTimeInMillis Current time since epoch in milliseconds
	 * @return Timestamp (ms since epoch) of beginning of next UTC hour
	 */
	static long calcNextHourMillis(final long curTimeInMillis) {
		final long hoursSinceEpoch = curTimeInMillis / 3600000L;
		return (hoursSinceEpoch + 1) * 3600000L;
	}
}