package com.mistrycapital.cryptobot.accounting;

public interface PositionsProvider {
	/**
	 * @return Mapping from Currency to amount available to trade. Note that this array can be mutated
	 */
	double[] getAvailable();

	/**
	 * @return Mapping from Currency to balance. Note that this array can be mutated
	 */
	double[] getBalance();
}
