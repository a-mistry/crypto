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
	/** Number of bids canceled */
	public long bidCancelCount;
	/** Number of asks canceled */
	public long askCancelCount;
	/** Size of bids canceled */
	public double bidCancelSize;
	/** Size of asks canceled */
	public double askCancelSize;
	/** Return over interval */
	public double ret;

	void reset(final long startTimeMicros, final long endTimeMicros, final double lastPrice, final double volume,
		final double vwap, final long bidTradeCount, final long askTradeCount, final double bidTradeSize,
		final double askTradeSize, final long bidCancelCount, final long askCancelCount, final double bidCancelSize,
		final double askCancelSize, final double ret)
	{
		this.startTimeMicros = startTimeMicros;
		this.endTimeMicros = endTimeMicros;
		this.lastPrice = lastPrice;
		this.volume = volume;
		this.vwap = vwap;
		this.bidTradeCount = bidTradeCount;
		this.askTradeCount = askTradeCount;
		this.bidTradeSize = bidTradeSize;
		this.askTradeSize = askTradeSize;
		this.bidCancelCount = bidCancelCount;
		this.askCancelCount = askCancelCount;
		this.bidCancelSize = bidCancelSize;
		this.askCancelSize = askCancelSize;
		this.ret = ret;
	}
}