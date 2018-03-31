package com.mistrycapital.cryptobot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeriodicEvaluatorTest {

	@Test
	void calcNextIntervalMicros() {
		assertEquals(60, PeriodicEvaluator.INTERVAL_SECONDS);
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520640060000L, PeriodicEvaluator.calcNextIntervalMillis(timeMs));
		assertEquals(1520640060000L, PeriodicEvaluator.calcNextIntervalMillis(timeMs + 12345L));
	}

}