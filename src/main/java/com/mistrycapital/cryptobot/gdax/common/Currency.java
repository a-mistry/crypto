package com.mistrycapital.cryptobot.gdax.common;

public enum Currency {
	USD(0,false),
	EUR(1,false),
	GBP(2,false),
	BCH(3,true),
	BTC(4,true),
	ETH(5,true),
	LTC(6,true);

	private final int index;
	private final boolean isCrypto;

	Currency(int index, boolean isCrypto) {
		this.index = index;
		this.isCrypto = isCrypto;
	}

	/**
	 * @return Index that can be used to look up in an array
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return true if this currency is a crypto currency
	 */
	public boolean isCrypto() {
		return isCrypto;
	}

	/**
	 * @return Product for cyrpto-USD pair, if exists, null otherwise
	 */
	public Product getUsdProduct() {
		return isCrypto ? Product.parse(toString() + "-USD") : null;
	}

	public static final Currency[] FAST_VALUES = Currency.values();
	public static final int count = FAST_VALUES.length;
}