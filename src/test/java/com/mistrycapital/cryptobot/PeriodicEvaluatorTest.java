package com.mistrycapital.cryptobot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeriodicEvaluatorTest {

	@Test
	void calcNextIntervalMicros() {
		assertEquals(60, PeriodicEvaluator.INTERVAL_SECONDS);
		long timeMicros = 1520640000000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520640060000000L, PeriodicEvaluator.calcNextIntervalMicros(timeMicros));
		assertEquals(1520640060000000L, PeriodicEvaluator.calcNextIntervalMicros(timeMicros + 12345000L));
	}

}