package com.mistrycapital.cryptobot.book;

/**
 * Snapshot of best bid/offer
 */
public class BBO {
	public double bidPrice;
	public double bidSize;
	public double askPrice;
	public double askSize;
	
	public double midPrice() {
		return (bidPrice + askPrice) / 2;
	}

	void reset(final double bidPrice, final double askPrice, final double bidSize, final double askSize) {
		this.bidPrice = bidPrice;
		this.askPrice = askPrice;
		this.bidSize = bidSize;
		this.askSize = askSize;
	}
}