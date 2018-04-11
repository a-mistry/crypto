package com.mistrycapital.cryptobot.accounting;

import com.mistrycapital.cryptobot.gdax.common.Currency;

/**
 * Tracks positions. Note that this class is not thread-safe
 */
public class Accountant {
	private double[] available;
	private double[] balance;

	public Accountant(PositionsProvider positionsProvider) {
		available = positionsProvider.getAvailable();
		balance = positionsProvider.getBalance();
	}

	public double getAvailable(Currency currency) {
		return available[currency.getIndex()];
	}

	public double getBalance(Currency currency) {
		return balance[currency.getIndex()];
	}

	public void recordTrade(Currency source, double sourcePosChange, Currency dest, double destPosChange) {
		available[source.getIndex()] += sourcePosChange;
		available[dest.getIndex()] += destPosChange;
		balance[source.getIndex()] += sourcePosChange;
		balance[dest.getIndex()] += destPosChange;
	}
}
