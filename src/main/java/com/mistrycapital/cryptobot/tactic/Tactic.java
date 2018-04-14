package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Determines trades based on our view of the world. Note that this class is NOT thread safe.
 */
public class Tactic {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Accountant accountant;

	private final double[] inThresholds;
	private final double[] outThresholds;
	private final double[] pctAllocation;

	public Tactic(MCProperties properties, Accountant accountant) {
		this.accountant = accountant;
		final double defaultInThreshold = properties.getDoubleProperty("tactic.inThreshold.default");
		final double defaultOutThreshold = properties.getDoubleProperty("tactic.outThreshold.default");
		final double defaultPctAllocation = properties.getDoubleProperty("tactic.pctAllocation.default");
		inThresholds = new double[Product.count];
		outThresholds = new double[Product.count];
		pctAllocation = new double[Product.count];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			inThresholds[productIndex] =
				properties.getDoubleProperty("tactic.inThreshold." + product, defaultInThreshold);
			outThresholds[productIndex] =
				properties.getDoubleProperty("tactic.outThreshold." + product, defaultOutThreshold);
			pctAllocation[productIndex] =
				properties.getDoubleProperty("tactic.pctAllocation." + product, defaultPctAllocation);
		}
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
				if(trades == null) trades = new ArrayList<>(Product.count);
				final double amount = accountant.getAvailable(product.getCryptoCurrency());
				trades.add(new TradeInstruction(product, amount, OrderSide.SELL));
			}
			if(!in && shouldBeIn) {
				if(trades == null) trades = new ArrayList<>(Product.count);
				final double amount = calcBuyAmount(snapshot, product);
				trades.add(new TradeInstruction(product, amount, OrderSide.BUY));
			}
		}
		return trades;
	}

	/**
	 * Calculates how much to buy, given that we don't want the position in any product to be more than the
	 * given allocation percentage
	 */
	private double calcBuyAmount(final ConsolidatedSnapshot snapshot, final Product product) {
		final double dollarsAvailable = accountant.getAvailable(Currency.USD);
		final double maxPositionUsd = accountant.getPositionValueUsd(snapshot) * pctAllocation[product.getIndex()];
		final double dollarsToBuy = Math.min(dollarsAvailable, maxPositionUsd);
		final double askPrice = snapshot.getProductSnapshot(product).askPrice;
		return dollarsToBuy / askPrice;
	}
}
