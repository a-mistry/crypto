package com.mistrycapital.cryptobot.gdax.client;

import java.util.Locale;

public enum TimeInForce {
	GTC("Good till canceled"), // this is the default
	GTT("Good till time"),
	IOC("Immediate or cancel"),
	FOK("Fill or kill");

	private final String humanName;

	TimeInForce(String humanName) {
		this.humanName = humanName;
	}

	public String getHumanName() {
		return humanName;
	}

	public static TimeInForce parse(String timeInForceString) {
		return valueOf(timeInForceString.toUpperCase(Locale.US));
	}

	public static final TimeInForce defaultValue = GTC;
}
