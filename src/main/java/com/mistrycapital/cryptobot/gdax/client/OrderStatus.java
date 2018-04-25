package com.mistrycapital.cryptobot.gdax.client;

import java.util.Locale;

public enum OrderStatus {
	OPEN,
	PENDING,
	ACTIVE,
	SETTLED,
	DONE;

	public static OrderStatus parse(String statusString) {
		return valueOf(statusString.toUpperCase(Locale.US));
	}
}
