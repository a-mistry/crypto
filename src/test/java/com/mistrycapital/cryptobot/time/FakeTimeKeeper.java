package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class FakeTimeKeeper implements TimeKeeper {
	private Instant now;
	
	public FakeTimeKeeper(Instant now) {
		this.now = now;
	}
	
	public void setTime(Instant now) {
		this.now = now;
	}
	
	@Override
	public Instant now() {
		return now;
	}
}