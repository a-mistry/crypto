package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Stores a daily csv file with interval data
 */
public class IntervalDataAppender extends CommonFileAppender {
	public IntervalDataAppender(final Path dataDir, final String baseFilename, final TimeKeeper timeKeeper) {
		super(timeKeeper, dataDir, baseFilename, ".csv", RollingPolicy.DAILY, FlushPolicy.FLUSH_EACH_WRITE);
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		System.err.println("adding ROOT INTERVAL DATA HEADER------------");
		append(IntervalData.csvHeaderRow());
	}
}