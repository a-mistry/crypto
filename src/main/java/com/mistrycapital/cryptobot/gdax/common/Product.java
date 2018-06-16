package com.mistrycapital.cryptobot.gdax.common;

public enum Product {
	BTC_USD(0,"BTC-USD",Currency.BTC),
	ETH_USD(1,"ETH-USD",Currency.ETH),
	BCH_USD(2,"BCH-USD",Currency.BCH),
	LTC_USD(3,"LTC-USD",Currency.LTC);
	
	private final int index;
	private final String strValue;
	private final Currency cryptoCurrency;
	
	Product(int index, String strValue, Currency cryptoCurrency) {
		this.index = index;
		this.strValue = strValue;
		this.cryptoCurrency = cryptoCurrency;
	}
	
	public final static Product[] FAST_VALUES = Product.values();
	public final static int count = FAST_VALUES.length;

	/**
	 * @return Index that can be used to look up in an array
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return The crypto currency leg of this product (other leg is USD)
	 */
	public Currency getCryptoCurrency() {
		return cryptoCurrency;
	}

	@Override
	public String toString() {
		return strValue;
	}
	
	public static Product parse(String productString) {
		// The following line was slow so we do a more complicated matching
		// return Product.valueOf(productString.replace("-","_"));
		char firstChar = productString.charAt(0);
		switch(firstChar) {
			case 'B':
				char secondChar = productString.charAt(1);
				return secondChar == 'T' ? BTC_USD  : BCH_USD;
			case 'E':
				return ETH_USD;
			case 'L':
				return LTC_USD;
			default:
				return Product.valueOf(productString.replace("-","_"));
		}
	}
}