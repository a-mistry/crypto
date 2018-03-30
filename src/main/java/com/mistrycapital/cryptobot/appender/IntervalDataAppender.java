package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Stores a daily csv file with interval data
 */
public class IntervalDataAppender extends CommonFileAppender {
	private final String headerRow;

	public IntervalDataAppender(final Path dataDir, final String baseFilename, final String headerRow,
		final TimeKeeper timeKeeper)
		throws IOException
	{
		super(timeKeeper, dataDir, baseFilename, ".csv", RollingPolicy.DAILY);
		this.headerRow = headerRow;
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		append(headerRow);
	}
}