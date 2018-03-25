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

	/**
	 * Creates a new appender and opens the output file for writing. This appender will
	 * <ul>
	 * <li>Write a new file every hour</li>
	 * <li>New files start with the order book snapshot for each product</li>
	 * <li>Archive old files into one zip per date</li>
	 * <li>The filename of the log is baseFilename-yyyy-mm-dd-HH-extension</li>
	 * <li>The zip file per day will be baseFilename-yyyy-mm-dd.zip</li>
	 * </ul>
	 *
	 * @param logDir           Directory to put the log file
	 * @param baseFilename     Filename to append to
	 * @param extension        Extension of the file (can include or exclude the period)
	 * @param timeKeeper       Used to keep track of what file to write to
	 * @param orderBookManager Used to get order book snapshots at the start of a new file
	 * @throws IOException
	 */
	public GdaxMessageAppender(final Path logDir, final String baseFilename, String extension,
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
	public boolean rollIfNeeded()
		throws IOException
	{
		if(timeKeeper.epochMs() < nextHourMillis) {
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

		final Path logFile = logDir.resolve(getLogFileNameForCurrentTime());
		writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
			StandardOpenOption.APPEND, StandardOpenOption.WRITE);

		nextHourMillis = calcNextHourMillis(timeKeeper.epochMs());
	}

	String getLogFileNameForCurrentTime() {
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(timeKeeper.now(), ZoneOffset.UTC);

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