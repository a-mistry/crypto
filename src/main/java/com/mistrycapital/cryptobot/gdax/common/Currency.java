package com.mistrycapital.cryptobot.gdax.common;

public enum Currency {
	USD(0),
	EUR(1),
	GBP(2),
	BCH(3),
	BTC(4),
	ETH(5),
	LTC(6);

	private final int index;

	Currency(int index) {
		this.index = index;
	}

	/**
	 * @return Index that can be used to look up in an array
	 */
	public int getIndex() {
		return index;
	}

	public static final Currency[] FAST_VALUES = Currency.values();
	public static final int count = FAST_VALUES.length;
}