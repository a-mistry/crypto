package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Appends messages to a log file, with a new file created with the frequency specified in the RollingPolicy
 * Note that this implementation is NOT thread safe!
 */
abstract class CommonFileAppender implements FileAppender {
	private final TimeKeeper timeKeeper;
	private final Path dataDir;
	private final String baseFilename;
	private final String extension;
	private final RollingPolicy rollingPolicy;
	private final FlushPolicy flushPolicy;
	private BufferedWriter writer;
	private long nextRollMillis;

	CommonFileAppender(final TimeKeeper timeKeeper, final Path dataDir, final String baseFilename, String extension,
		final RollingPolicy rollingPolicy, final FlushPolicy flushPolicy)
	{
		this.timeKeeper = timeKeeper;
		this.dataDir = dataDir;
		this.rollingPolicy = rollingPolicy;
		this.flushPolicy = flushPolicy;
		this.baseFilename = baseFilename;
		if(extension.startsWith(".")) {
			extension = extension.substring(1, extension.length());
		}
		this.extension = extension;
	}

	@Override
	public void open()
		throws IOException
	{
		final Path dataFile = dataDir.resolve(getFileNameForCurrentTime());
		final boolean exists = Files.exists(dataFile);
		writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
			StandardOpenOption.APPEND, StandardOpenOption.WRITE);
		if(!exists)
			addNewFileHeader();

		nextRollMillis = rollingPolicy.calcNextRollMillis(timeKeeper.epochMs());
	}

	@Override
	public void append(final String msg)
		throws IOException
	{
		rollIfNeeded();

		writer.append(msg + '\n');
		if(flushPolicy == FlushPolicy.FLUSH_EACH_WRITE)
			writer.flush();
	}

	@Override
	public void close()
		throws IOException
	{
		if(writer != null) {
			writer.close();
			writer = null;
		}
	}

	/**
	 * Rolls to the next hour file if needed
	 *
	 * @return true if we rolled to a new file
	 */
	public boolean rollIfNeeded()
		throws IOException
	{
		if(timeKeeper.epochMs() < nextRollMillis) {
			return false;
		}

		close();
		open();

		return true;
	}

	/**
	 * @return File name to use for the output file, given the current time
	 */
	protected String getFileNameForCurrentTime() {
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(timeKeeper.now(), ZoneOffset.UTC);

		StringBuilder builder = new StringBuilder();
		builder.append(baseFilename);
		builder.append('-');
		builder.append(rollingPolicy.format(dateTime));
		builder.append('.');
		builder.append(extension);
		return builder.toString();
	}

	/**
	 * This is called when a new file is created. It can be used for example to add a header to the file. It
	 * is not called if the file already exists from before.
	 */
	abstract protected void addNewFileHeader()
		throws IOException;
}
