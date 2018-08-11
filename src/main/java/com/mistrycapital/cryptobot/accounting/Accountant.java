package com.mistrycapital.cryptobot.accounting;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;

/**
 * Tracks positions. This class is thread safe
 */
public class Accountant {
	private final PositionsProvider positionsProvider;
	private double[] available;
	private double[] balance;

	public Accountant(PositionsProvider positionsProvider) {
		this.positionsProvider = positionsProvider;
		refreshPositions();
	}

	public synchronized double getAvailable(Currency currency) {
		return available[currency.getIndex()];
	}

	public synchronized double getBalance(Currency currency) {
		return balance[currency.getIndex()];
	}

	public void refreshPositions() {
		// TODO: make this a completablefuture
		double[] availableFound = positionsProvider.getAvailable();
		double[] balanceFound = positionsProvider.getBalance();
		synchronized(this) {
			available = availableFound;
			balance = balanceFound;
		}
	}

	public synchronized void recordTrade(Currency source, double sourcePosChange, Currency dest, double destPosChange) {
		available[source.getIndex()] += sourcePosChange;
		available[dest.getIndex()] += destPosChange;
		balance[source.getIndex()] += sourcePosChange;
		balance[dest.getIndex()] += destPosChange;
	}

	public synchronized double getPositionValueUsd(ConsolidatedSnapshot snapshot) {
		double value = balance[Currency.USD.getIndex()];
		for(Product product : Product.FAST_VALUES) {
			Currency crypto = product.getCryptoCurrency();
			final ProductSnapshot productSnapshot = snapshot.getProductSnapshot(product);
			// If book data is not available, assume any position is worth zero conservatively
			final double midPrice = productSnapshot == null || Double.isNaN(productSnapshot.midPrice)
				? 0.0 : productSnapshot.midPrice;
			value += balance[crypto.getIndex()] * midPrice;
		}
		return value;
	}
}
