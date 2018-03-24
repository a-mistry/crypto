package com.mistrycapital.cryptobot.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public interface TimeKeeper {
	/**
	 * @return Current instant. Implementing classes should choose whether this is real time or simulated
	 */
	Instant now();

	/**
	 * @return Current time in milliseconds since epoch
	 */
	default long epochMs() {
		return now().toEpochMilli();
	}

	/**
	 * @return Current time in ISO 8601 format
	 */
	default String iso8601() {
		ZonedDateTime now = ZonedDateTime.ofInstant(now(), ZoneOffset.UTC);
		return String.format("%4d-%02d-%02dT%02d:%02d:%02d.%06dZ",
			now.getYear(),
			now.getMonthValue(),
			now.getDayOfMonth(),
			now.getHour(),
			now.getMinute(),
			now.getSecond(),
			now.getNano() / 1000L
		);
	}
}