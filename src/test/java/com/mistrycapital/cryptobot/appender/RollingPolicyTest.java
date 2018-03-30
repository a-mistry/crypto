package com.mistrycapital.cryptobot.appender;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RollingPolicyTest {
	@Test
	void shouldCalcNextRollMillis() {
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520643600000L, RollingPolicy.HOURLY.calcNextRollMillis(timeMs));
		assertEquals(1520643600000L, RollingPolicy.HOURLY.calcNextRollMillis(timeMs + 100000L));
		assertEquals(1520726400000L, RollingPolicy.DAILY.calcNextRollMillis(timeMs));
		assertEquals(1520726400000L, RollingPolicy.DAILY.calcNextRollMillis(timeMs + 100000L));
	}

	@Test
	void shouldFormatDateTime() {
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMs), ZoneOffset.UTC);
		assertEquals("2018-03-10-00", RollingPolicy.HOURLY.format(dateTime));
		assertEquals("2018-03-10", RollingPolicy.DAILY.format(dateTime));
	}
}