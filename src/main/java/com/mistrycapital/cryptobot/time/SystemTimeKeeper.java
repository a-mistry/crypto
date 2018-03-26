package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class SystemTimeKeeper implements TimeKeeper {
	private final long nanoOffset;

	public SystemTimeKeeper() {
		final Instant instant = Instant.now();
		final long startEpochNanos = instant.getEpochSecond()*1000000000L + instant.getNano();
		nanoOffset = startEpochNanos - System.nanoTime();
	}

	@Override
	public final Instant now() {
		return Instant.now();
	}

	@Override
	public final long epochMs() {
		return System.currentTimeMillis();
	}

	@Override
	public final long epochNanos() {
		return nanoOffset + System.nanoTime();
	}
}
