package com.mistrycapital.cryptobot.time;

import java.time.Instant;

public class SystemTimeKeeper implements TimeKeeper {
	@Override
	public Instant now() {
		return Instant.now();
	}
}
