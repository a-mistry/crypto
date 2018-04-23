package com.mistrycapital.cryptobot.time;

import com.mistrycapital.cryptobot.util.MCProperties;

public class Intervalizer {
	private static long HOUR_MILLIS = 60 * 60 * 1000L;
	private static long DAY_MILLIS = 24 * HOUR_MILLIS;

	/** Total amount of history to track */
	private final int secondsToKeep;
	/** Length of each interval of data aggregation */
	private final int intervalSeconds;
	/** Number of intervals in history */
	private final int historyIntervals;

	public Intervalizer(MCProperties properties) {
		secondsToKeep = properties.getIntProperty("history.secondsToKeep");
		intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		historyIntervals = secondsToKeep / intervalSeconds;
	}

	/**
	 * Used to determine when a sampling interval has ended
	 *
	 * @param curMillis Current time in millis since epoch
	 * @return Time when the next interval will end, in millis since epoch
	 */
	public final long calcNextIntervalMillis(long curMillis) {
		return (curMillis / 1000L / intervalSeconds + 1) * 1000L * intervalSeconds;
	}

	public final long calcNextHourMillis(long curMillis) {
		return (curMillis / HOUR_MILLIS + 1) * HOUR_MILLIS;
	}

	public final long calcNextDayMillis(long curMillis) {
		return (curMillis / DAY_MILLIS + 1) * DAY_MILLIS;
	}

	/**
	 * @return Number of intervals to store in history
	 */
	public final int getHistoryIntervals() {
		return historyIntervals;
	}
}
