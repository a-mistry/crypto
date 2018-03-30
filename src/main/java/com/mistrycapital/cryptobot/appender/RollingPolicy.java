package com.mistrycapital.cryptobot.appender;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Instructs the appender on when to roll to a new file
 */
enum RollingPolicy {
	HOURLY(3600000L, "yyyy-MM-dd-HH"),
	DAILY(24 * 3600000L, "yyyy-MM-dd");

	private final long periodLengthMs;
	private final DateTimeFormatter dateTimeFormatter;

	RollingPolicy(long periodLengthMs, String dateTimePattern) {
		this.periodLengthMs = periodLengthMs;
		dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern);
	}

	/**
	 * @return Date/time formatted to use in a filename consistent with the policy. For example,
	 * on a daily policy, the time is returned as yyyy-MM-dd
	 */
	public String format(ZonedDateTime now) {
		return dateTimeFormatter.format(now);
	}

	/**
	 * @param curTimeInMillis Current time since epoch in milliseconds
	 * @return Timestamp (ms since epoch) of beginning of next UTC period
	 */
	public long calcNextRollMillis(final long curTimeInMillis) {
		final long hoursSinceEpoch = curTimeInMillis / periodLengthMs;
		return (hoursSinceEpoch + 1) * periodLengthMs;
	}
}