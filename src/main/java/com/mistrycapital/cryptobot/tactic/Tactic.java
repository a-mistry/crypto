package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * Determines trades based on our view of the world. Note that this class is NOT thread safe.
 */
public class Tactic {
	private final Accountant accountant;

	private final double[] inThresholds = new double[] { -0.05, -0.05, -0.05, -0.05 };
	private final double[] outThresholds = new double[] { -0.15, -0.15, -0.15, -0.15 };
	private final double[] pctAllocation = new double[] { 0.25, 0.25, 0.25, 0.25 };

	public Tactic(Accountant accountant) {
		this.accountant = accountant;
	}

	/**
	 * Decides what trades to make, if any.
	 *
	 * @return Array of trades to make. Assumes trades will be executed in the same order as the array. Do not
	 * execute out of order or the trades may not be legitimate
	 */
	public List<TradeInstruction> decideTrades(ConsolidatedSnapshot snapshot, double[] forecasts) {
		List<TradeInstruction> trades = null;
		for(Product product : Product.FAST_VALUES) {
			final int productIndex = product.getIndex();
			final boolean in = accountant.getAvailable(product.getCryptoCurrency()) > 0.0;
			final double forecast = forecasts[productIndex];
			final boolean shouldBeIn = forecast > inThresholds[productIndex];
			final boolean shouldBeOut = forecast < outThresholds[productIndex];

			if(in && shouldBeOut) {
				if(trades==null) trades = new ArrayList<>(Product.count);
				final double amount = accountant.getAvailable(product.getCryptoCurrency());
				trades.add(new TradeInstruction(product, amount, OrderSide.SELL));
			}
			if(!in && shouldBeIn) {
				if(trades==null) trades = new ArrayList<>(Product.count);
				// TODO: this is incorrect - need to account for whole portfolio
				final double amount = accountant.getAvailable(Currency.USD) * pctAllocation[productIndex];
				trades.add(new TradeInstruction(product, amount, OrderSide.BUY));
			}
		}
		return trades;
	}
}
