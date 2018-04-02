package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.time.Instant;

public class SimTimeKeeper implements TimeKeeper {
	private volatile long timeNanos = 0;

	/** Advances time to the given time in nanos since epoch */
	public void advanceTime(long timeNanos) {
		this.timeNanos = timeNanos;
	}

	@Override
	public Instant now() {
		return Instant.ofEpochSecond(timeNanos/1000000000L, timeNanos % 1000000000L);
	}

	@Override
	public long epochMs() {
		return timeNanos / 1000000L;
	}

	@Override
	public long epochNanos() {
		return timeNanos;
	}
}
