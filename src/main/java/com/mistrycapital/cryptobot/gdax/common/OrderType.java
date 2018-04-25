package com.mistrycapital.cryptobot.gdax.common;

import java.util.Locale;

public enum OrderType {
	MARKET,
	LIMIT;
	
	public static OrderType parse(String typeString) {
		return OrderType.valueOf(typeString.toUpperCase(Locale.US));
	}
}
