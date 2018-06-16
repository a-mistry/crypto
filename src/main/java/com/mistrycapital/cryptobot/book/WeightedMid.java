package com.mistrycapital.cryptobot.book;

/**
 * Used to efficiently retrieve weighted mid prices from the order book
 */
public class WeightedMid {
	/** This is the input into the mid calculation. Number of levels to include in the calculation */
	public int numLevels;

	/** Weighted mid price */
	public double weightedMidPrice;
	/** Aggregate size of levels considered */
	public double size;
}
