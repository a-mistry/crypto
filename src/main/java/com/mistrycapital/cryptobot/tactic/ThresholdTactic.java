package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.Aggression;
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
 * This tactic decides to go all in (subject to position limits) or out based on the forecast for each product
 */
public class ThresholdTactic implements Tactic {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Accountant accountant;

	private final double[] inThresholds;
	private final double[] outThresholds;
	private final double[] pctAllocation;

	public ThresholdTactic(MCProperties properties, Accountant accountant) {
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
			log.debug("Tactic " + product + " in at " + inThresholds[productIndex] + " out at " +
				outThresholds[productIndex] + " alloc " + (100 * pctAllocation[productIndex]) + "%");
		}
	}

	/**
	 * Decides what trades to make, if any.
	 *
	 * @return Array of trades to make. Assumes trades will be executed in the same order as the array. Do not
	 * execute out of order or the trades may not be legitimate
	 */
	@Override
	public List<TradeInstruction> decideTrades(ConsolidatedSnapshot snapshot, double[] forecasts) {
		List<TradeInstruction> trades = null;
		for(Product product : Product.FAST_VALUES) {
			final int productIndex = product.getIndex();
			final boolean in = accountant.getAvailable(product.getCryptoCurrency()) > 0.0;
			final double forecast = forecasts[productIndex];

			final boolean shouldBeIn = forecast > inThresholds[productIndex];
			final boolean shouldBeOut = forecast < outThresholds[productIndex];

			if(Double.isNaN(forecast)) {
				// do nothing
			} else if(in && shouldBeOut) {
				if(trades == null) trades = new ArrayList<>(Product.count);
				final double amount = accountant.getAvailable(product.getCryptoCurrency());
				trades.add(new TradeInstruction(product, amount, OrderSide.SELL, Aggression.TAKE));
			}
			if(!in && shouldBeIn) {
				if(trades == null) trades = new ArrayList<>(Product.count);
				final double amount = calcBuyAmount(snapshot, product);
				trades.add(new TradeInstruction(product, amount, OrderSide.BUY, Aggression.TAKE));
			}
		}
		return trades;
	}

	@Override
	public void notifyFill(final TradeInstruction instruction, final Product product, final OrderSide orderSide,
		final double amount, final double price)
	{
		// do nothing
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
