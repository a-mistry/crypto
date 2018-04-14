package com.mistrycapital.cryptobot.accounting;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;

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

	public double getPositionValueUsd(ConsolidatedSnapshot snapshot) {
		double value = balance[Currency.USD.getIndex()];
		for(Product product : Product.FAST_VALUES) {
			Currency crypto = product.getCryptoCurrency();
			final double midPrice = snapshot.getProductSnapshot(product).midPrice;
			value += balance[crypto.getIndex()] * midPrice;
		}
		return value;
	}
}
