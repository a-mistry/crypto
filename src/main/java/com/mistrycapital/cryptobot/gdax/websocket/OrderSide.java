package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.Locale;

public enum OrderSide {
	BUY,
	SELL;
	
	public static OrderSide parse(String sideString) {
		return OrderSide.valueOf(sideString.toUpperCase(Locale.US));
	}
}
