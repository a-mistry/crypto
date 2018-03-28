package com.mistrycapital.cryptobot.dynamic;

/**
 * Dynamic product data for a given interval
 */
public class IntervalData {
	/** Interval start time since epoch in microseconds */
	public long startTimeMicros;
	/** Interval end time since epoch in microseconds */
	public long endTimeMicros;
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

	void reset(final long startTimeMicros, final long endTimeMicros) {
		this.startTimeMicros = startTimeMicros;
		this.endTimeMicros = endTimeMicros;
		lastPrice = Double.NaN;
		ret = Double.NaN;
		volume = 0.0;
		vwap = 0.0;
		bidTradeCount = askTradeCount = newBidCount = newAskCount = 0;
		bidTradeSize = askTradeSize = newBidSize = newAskSize = 0.0;
		bidCancelCount = askCancelCount = 0;
		bidCancelSize = askCancelSize = 0.0;
	}

	void copyFrom(IntervalData b) {
		startTimeMicros = b.startTimeMicros;
		endTimeMicros = b.endTimeMicros;
		lastPrice = b.lastPrice;
		ret = b.ret;
		volume = b.volume;
		vwap = b.vwap;
		bidTradeCount = b.bidTradeCount;
		askTradeCount = b.askTradeCount;
		bidTradeSize = b.bidTradeSize;
		askTradeSize = b.askTradeSize;
		newBidCount = b.newBidCount;
		newAskCount = b.newAskCount;
		newBidSize = b.newBidSize;
		newAskSize = b.newAskSize;
		bidCancelCount = b.bidCancelCount;
		askCancelCount = b.askCancelCount;
		bidCancelSize = b.bidCancelSize;
		askCancelSize = b.askCancelSize;
	}
}