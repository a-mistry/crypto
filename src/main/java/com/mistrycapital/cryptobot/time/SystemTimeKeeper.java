package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class SystemTimeKeeper implements TimeKeeper {
	@Override
	public final Instant now() {
		return Instant.now();
	}

	@Override
	public final long epochMs() {
		return System.currentTimeMillis();
	}
}
