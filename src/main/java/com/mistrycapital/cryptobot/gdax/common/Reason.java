package com.mistrycapital.cryptobot.gdax.common;

import java.util.Locale;

public enum Reason {
	FILLED,
	CANCELED;

	public static Reason parse(String reasonString) {
		return valueOf(reasonString.toUpperCase(Locale.US));
	}
}
