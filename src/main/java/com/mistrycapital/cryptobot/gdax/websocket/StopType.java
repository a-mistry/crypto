package com.mistrycapital.cryptobot.gdax.websocket;

public enum StopType {
	LOSS,
	ENTRY;

	public static StopType parse(String typeString) {
		// this is faster than uppercasing
		if("loss".equals(typeString))
			return LOSS;
		if("entry".equals(typeString))
			return ENTRY;
		return StopType.valueOf(typeString);
	}
}
