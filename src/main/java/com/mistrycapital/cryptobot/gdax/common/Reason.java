package com.mistrycapital.cryptobot.gdax.common;

public enum Reason {
	FILLED,
	CANCELED;

	public static Reason parse(String reasonString) {
		// this is faster than uppercasing
		if("filled".equals(reasonString))
			return FILLED;
		if("canceled".equals(reasonString))
			return CANCELED;
		return valueOf(reasonString);
	}
}
