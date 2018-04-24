package com.mistrycapital.cryptobot.time;

import com.mistrycapital.cryptobot.util.MCProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntervalizerTest {

	@Test
	void shouldCalcNextIntervalMicros() {
		MCProperties properties = new MCProperties();
		Intervalizer intervalizer = new Intervalizer(properties);

		assertEquals(
			properties.getIntProperty("history.secondsToKeep") / properties.getIntProperty("history.intervalSeconds"),
			intervalizer.getHistoryIntervals());
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520640060000L, intervalizer.calcNextIntervalMillis(timeMs));
		assertEquals(1520640060000L, intervalizer.calcNextIntervalMillis(timeMs + 12345L));
	}

	@Test
	void shouldCalcNextHourAndDay() {
		MCProperties properties = new MCProperties();
		Intervalizer intervalizer = new Intervalizer(properties);

		assertEquals(1524536902000L, intervalizer.calcNextDayMillis(1524536902000L)); // 4/24 2:28
		assertEquals(1524536902000L, intervalizer.calcNextHourMillis(1524536902000L));
		assertEquals(1524542400000L, intervalizer.calcNextHourMillis(1524536902000L));
	}
}