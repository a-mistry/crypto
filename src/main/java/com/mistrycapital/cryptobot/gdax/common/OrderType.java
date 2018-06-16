package com.mistrycapital.cryptobot.gdax.common;

public enum OrderType {
	MARKET,
	LIMIT;
	
	public static OrderType parse(String typeString) {
		// this is faster than uppercasing
		if("market".equals(typeString))
			return MARKET;
		if("limit".equals(typeString))
			return LIMIT;
		return OrderType.valueOf(typeString);
	}
}
