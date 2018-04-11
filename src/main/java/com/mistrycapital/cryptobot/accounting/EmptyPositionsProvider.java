package com.mistrycapital.cryptobot.accounting;

import com.mistrycapital.cryptobot.gdax.common.Currency;

/**
 * Provides an "empty" book (consisting only of USD)
 */
public class EmptyPositionsProvider implements PositionsProvider {
	private final double usdAmount;

	public EmptyPositionsProvider(double usdAmount) {
		this.usdAmount = usdAmount;
	}

	@Override
	public double[] getAvailable() {
		return getBalance();
	}

	@Override
	public double[] getBalance() {
		double[] retValue = new double[Currency.count];
		retValue[Currency.USD.getIndex()] = usdAmount;
		return retValue;
	}
}
