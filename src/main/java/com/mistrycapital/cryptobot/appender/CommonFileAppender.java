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
	private BufferedWriter writer;
	private long nextRollMillis;

	/** Creates a new appender and opens the output file for writing */
	CommonFileAppender(final TimeKeeper timeKeeper, final Path dataDir, final String baseFilename, String extension,
		final RollingPolicy rollingPolicy)
		throws IOException
	{
		this.timeKeeper = timeKeeper;
		this.dataDir = dataDir;
		this.rollingPolicy = rollingPolicy;
		this.baseFilename = baseFilename;
		if(extension.startsWith(".")) {
			extension = extension.substring(1, extension.length());
		}
		this.extension = extension;
		openNewFile();
	}

	@Override
	public void append(final String msg)
		throws IOException
	{
		rollIfNeeded();

		writer.append(msg + '\n');
	}

	@Override
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
		if(timeKeeper.epochMs() < nextRollMillis) {
			return false;
		}

		openNewFile();
		addNewFileHeader();

		return true;
	}

	private void openNewFile()
		throws IOException
	{
		if(writer != null) {
			writer.close();
		}

		final Path dataFile = dataDir.resolve(getFileNameForCurrentTime());
		writer = Files
			.newBufferedWriter(dataFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
				StandardOpenOption.WRITE);

		nextRollMillis = rollingPolicy.calcNextRollMillis(timeKeeper.epochMs());
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
	 * This is called when a new file is created. It can be used for example to add a header to the file
	 */
	abstract protected void addNewFileHeader()
		throws IOException;
}
