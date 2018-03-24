package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.Locale;

public enum OrderType {
	MARKET,
	LIMIT;
	
	public static OrderType parse(String typeString) {
		return OrderType.valueOf(typeString.toUpperCase(Locale.US));
	}
}
