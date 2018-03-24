package com.mistrycapital.cryptobot.gdax.websocket;

public enum Product {
	BTC_USD(0,"BTC-USD"),
	ETH_USD(1,"ETH-USD"),
	BCH_USD(2,"BCH-USD"),
	LTC_USD(3,"LTC-USD");
	
	private final int index;
	private final String strValue;
	
	private Product(int index, String strValue) {
		this.index = index;
		this.strValue = strValue;
	}
	
	public final static Product[] FAST_VALUES = Product.values();
	public final static int count = FAST_VALUES.length;

	/**
	 * @return Index that can be used to look up in an array
	 */
	public int getIndex() {
		return index;
	}

	@Override
	public String toString() {
		return strValue;
	}
	
	public static Product parse(String productString) {
		return Product.valueOf(productString.replace("-","_"));
	}
}