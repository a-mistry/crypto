package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class SystemTimeKeeper implements TimeKeeper {
	private final long nanoOffset;

	public SystemTimeKeeper() {
		// need to capture System.nanoTime() right after millisecond switch to be as accurate as we can get
		long timeMs = System.currentTimeMillis() + 1;
		while(System.currentTimeMillis() < timeMs)
			; // wait
		nanoOffset = timeMs * 1000000L - System.nanoTime();
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
