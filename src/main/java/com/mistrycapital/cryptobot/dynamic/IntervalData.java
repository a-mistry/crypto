package com.mistrycapital.cryptobot.dynamic;

/**
 * Dynamic product data for a given interval
 */
public class IntervalData {
	/** Last trade price */
	public double lastPrice;
	/** Return over interval */
	public double ret;
	/** Volume */
	public double volume;
	/** VWAP */
	public double vwap;
	/** Number of trades executed at the bid */
	public long bidTradeCount;
	/** Number of trades executed at the ask */
	public long askTradeCount;
	/** Size of trades executed at the bid */
	public double bidTradeSize;
	/** Size of trades executed at the ask */
	public double askTradeSize;
	/** Number of new bids placed */
	public long newBidCount;
	/** Number of new asks placed */
	public long newAskCount;
	/** Size of new bids placed */
	public double newBidSize;
	/** Size of new asks placed */
	public double newAskSize;
	/** Number of bids canceled */
	public long bidCancelCount;
	/** Number of asks canceled */
	public long askCancelCount;
	/** Size of bids canceled */
	public double bidCancelSize;
	/** Size of asks canceled */
	public double askCancelSize;

	IntervalData() {
		lastPrice = Double.NaN;
		ret = Double.NaN;
	}
}