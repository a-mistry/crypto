package com.mistrycapital.cryptobot.gdax.common;

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

	/** @return 1 if buy, -1 if sell */
	public int sign() {
		return this == BUY ? 1 : -1;
	}

	public static OrderSide parse(String sideString) {
		// faster to do this than uppercase and use valueOf
		if("buy".equals(sideString))
			return BUY;
		if("sell".equals(sideString))
			return SELL;
		return valueOf(sideString);
	}
}
