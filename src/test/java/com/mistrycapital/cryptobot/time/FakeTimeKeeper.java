package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class FakeTimeKeeper implements TimeKeeper {
	private Instant now;

	public FakeTimeKeeper() {
		this(System.currentTimeMillis());
	}

	public FakeTimeKeeper(long epochMs) {
		now = Instant.ofEpochMilli(epochMs);
	}
	
	public void setTime(long epochMs) {
		now = Instant.ofEpochMilli(epochMs);
	}

	@Override
	public Instant now() {
		return now;
	}
}