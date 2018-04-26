package com.mistrycapital.cryptobot.gdax.client;

import java.util.Locale;

public enum SelfTradePreventionFlag {
	DC("Decrease and Cancel"), // this is the default
	CO("Cancel oldest"),
	CN("Cancel newest"),
	CB("Cancel both");

	private final String humanName;

	SelfTradePreventionFlag(String humanName) {
		this.humanName = humanName;
	}

	public String getHumanName() {
		return humanName;
	}

	public static SelfTradePreventionFlag parse(String stp) {
		return valueOf(stp.toUpperCase(Locale.US));
	}

	public static final SelfTradePreventionFlag defaultValue = DC;
}
