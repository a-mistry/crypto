package com.mistrycapital.cryptobot.gdax.common;

import java.util.Locale;

public enum OrderSide {
	BUY("B"),
	SELL("S");

	private final String shortStr;

	OrderSide(String shortStr) {
		this.shortStr = shortStr;
	}

	/** @return 1 letter abbreviation (B or S) */
	public String toShortString() {
		return shortStr;
	}

	public static OrderSide parse(String sideString) {
		return OrderSide.valueOf(sideString.toUpperCase(Locale.US));
	}
}
