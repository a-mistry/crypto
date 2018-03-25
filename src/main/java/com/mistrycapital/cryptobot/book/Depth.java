package com.mistrycapital.cryptobot.book;

/**
 * Stores data on book depth for various percentages from the midpoint
 */
public class Depth {
	/**
	 * This is the input into book depth calculations. This is the percentage from the midpoint within
	 * which we should record the depth. For example, if this value is 0.05, this means, we should
	 * get the number and size of orders within 5% of the midpoint and return them in the other fields.
	 */
	public double pctFromMid;

	/** Number of bids within given percent of mid */
	public int bidCount;
	/** Number of asks within given percent of mid */
	public int askCount;
	/** Size of bids within given percent of mid */
	public double bidSize;
	/** Size of asks within given percent of mid */
	public double askSize;

	void reset(final int bidCount, final int askCount, final double bidSize, final double askSize) {
		this.bidCount = bidCount;
		this.askCount = askCount;
		this.bidSize = bidSize;
		this.askSize = askSize;
	}
}
