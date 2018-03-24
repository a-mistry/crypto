package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.Locale;

public enum StopType {
	LOSS,
	ENTRY;
	
	public static StopType parse(String typeString) {
		return StopType.valueOf(typeString.toUpperCase(Locale.US));
	}
}
