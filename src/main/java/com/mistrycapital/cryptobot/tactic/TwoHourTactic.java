package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.Aggression;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Tactic that holds positions for two hours based on the forecast value
 */
public class TwoHourTactic implements Tactic {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final double EPSILON8 = 1e-8;

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	/** Number of intervals in two hours */
	private final int lookback;
	/** Amount the tactic wanted to purchase each period for each product */
	private final double[][] purchased;
	/** Current period */
	private int purchasedIndex;
	/** Percent allocation for each product */
	private final double[] pctAllocation;
	/** Buy above this threshold */
	private final double forecastThreshold;
	/** Are we using the version that buys in proportion to forecast */
	private final boolean buyForecastProportion;
	/** Do not buy extra above this threshold */
	private final double forecastUpperThreshold;
	/** Do not trade if we are not trading at least a minimum number of dollars */
	private final double tradeUsdThreshold;
	/** Scales our trades */
	private final double tradeScaleFactor;
	/** Outstanding orders */
	private final List<ActiveOrder> activeOrders;

	private class ActiveOrder {
		/** Time order was placed */
		long timeNanos;
		/** Original trade instruction */
		TradeInstruction instruction;
		/** Amount filled so far, as notified by the execution engine */
		double filledAmount;

		ActiveOrder(long timeNanos, TradeInstruction instruction) {
			this.timeNanos = timeNanos;
			this.instruction = instruction;
			filledAmount = 0;
		}

		/** @return Amount not yet filled */
		double getOutstandingAmount() {
			return instruction.getAmount() - filledAmount;
		}
	}

	public TwoHourTactic(MCProperties properties, TimeKeeper timeKeeper, Accountant accountant) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		lookback = 2 * 60 * 60 / intervalSeconds;
		purchased = new double[Product.count][lookback];
		purchasedIndex = 0;
		pctAllocation = new double[Product.count];
		final double defaultPctAllocation = properties.getDoubleProperty("tactic.pctAllocation.default");
		forecastThreshold = properties.getDoubleProperty("tactic.buyThreshold");
		buyForecastProportion = properties.getBooleanProperty("tactic.buyForecastProportion");
		forecastUpperThreshold = properties.getDoubleProperty("tactic.buyUpperThreshold");
		tradeUsdThreshold = properties.getDoubleProperty("tactic.tradeUsdThreshold");
		tradeScaleFactor = properties.getDoubleProperty("tactic.tradeScaleFactor");
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			pctAllocation[productIndex] =
				properties.getDoubleProperty("tactic.pctAllocation." + product, defaultPctAllocation);
			log.debug("Two hour tactic " + product + " alloc " + (100 * pctAllocation[productIndex]) + "%");
		}
		activeOrders = new LinkedList<>();
	}

	@Override
	public List<TradeInstruction> decideTrades(final ConsolidatedSnapshot snapshot, final double[] forecasts) {
		double totalPositionUsd = accountant.getPositionValueUsd(snapshot);
		List<TradeInstruction> trades = null;
		for(Product product : Product.FAST_VALUES) {
			final var productIndex = product.getIndex();
			final var askPrice = snapshot.getProductSnapshot(product).askPrice;

			purgeOlderThanOneHour();

			final var maxLong = totalPositionUsd * pctAllocation[productIndex] / askPrice;
			purchased[productIndex][purchasedIndex] = calcPurchase(maxLong, forecasts[productIndex]);

			final var goalPosition = sum(purchased[productIndex]);
			final var currentPosition = accountant.getBalance(product.getCryptoCurrency());
			final var outstandingPurchases = calcOutstandingPurchases(product);

			final var deltaPosition = goalPosition - currentPosition - outstandingPurchases;
			final var deltaPositionUsd = deltaPosition * askPrice;

			// This is to handle the situation where the tactic wanted to buy in the past but because of
			// bugs or rejected trades it was unable to do so, now it wants to catch up. But if the
			// forecast is bad, we should not buy regardless
			final var catchUpBadBuy = deltaPosition > 0 && forecasts[productIndex] < 0;

			if(Math.abs(deltaPositionUsd) > tradeUsdThreshold && !catchUpBadBuy) {
				if(trades == null)
					trades = new ArrayList<>(Product.count);
				final TradeInstruction instruction;
				if(deltaPosition > 0)
					instruction = new TradeInstruction(product, deltaPosition, OrderSide.BUY, Aggression.POST_ONLY,
						forecasts[productIndex]);
				else
					instruction = new TradeInstruction(product, -deltaPosition, OrderSide.SELL, Aggression.POST_ONLY,
						forecasts[productIndex]);
				trades.add(instruction);
				log.debug("Tactic decided to " + instruction.getOrderSide() + " " + instruction.getAmount() + " "
					+ product + " ask " + askPrice);
				activeOrders.add(new ActiveOrder(timeKeeper.epochNanos(), instruction));
			}
		}
		purchasedIndex = (purchasedIndex + 1) % lookback;
		return trades;
	}

	/** Removes orders that are older than one hour ago. Assumes orders are sorted by time */
	private void purgeOlderThanOneHour() {
		while(!activeOrders.isEmpty() &&
			activeOrders.get(0).timeNanos + 60 * 60 * 1000000000L <= timeKeeper.epochNanos()) {
			activeOrders.remove(0);
		}
	}

	/** @return Amount we want to go long this period, given the maximum long position and forecast */
	private double calcPurchase(final double maxLong, final double forecast) {
		return buyForecastProportion ? calcPurchaseProportionally(maxLong, forecast) :
			calcPurchaseWithToggle(maxLong, forecast);
	}

	private double calcPurchaseWithToggle(final double maxLong, final double forecast) {
		if(Double.isNaN(forecast) || forecast < forecastThreshold)
			return 0.0;

		return maxLong * Math.min(1.0, tradeScaleFactor / lookback);
	}

	private double calcPurchaseProportionally(final double maxLong, final double forecast) {
		if(Double.isNaN(forecast) || forecast < forecastThreshold)
			return 0.0;

		return maxLong * Math.min(1.0, tradeScaleFactor / lookback * 1000 *
			Math.min(forecastUpperThreshold, forecast - forecastThreshold));
	}

	/** @return Sum of the given array */
	private static double sum(double[] array) {
		double val = 0.0;
		for(double x : array) { val += x; }
		return val;
	}

	/** @return Any outstanding amount that will be purchased (sold if negative) if all active orders go through */
	private double calcOutstandingPurchases(Product product) {
		double outstanding = 0.0;
		for(ActiveOrder order : activeOrders) {
			if(order.instruction.getProduct() != product)
				continue;

			if(order.instruction.getOrderSide() == OrderSide.BUY)
				outstanding += order.getOutstandingAmount();
			else
				outstanding -= order.getOutstandingAmount();
		}
		return outstanding;
	}

	/** Note the amount purchased (negative if sold) for each instruction */
	@Override
	public void notifyFill(final TradeInstruction instruction, final Product product, final OrderSide orderSide,
		final double amount, final double price)
	{
		ActiveOrder finishedOrder = null;
		for(ActiveOrder order : activeOrders) {
			if(order.instruction == instruction) {
				order.filledAmount += amount;
				if(Math.abs(instruction.getAmount() - order.filledAmount) < EPSILON8)
					finishedOrder = order;
				break;
			}
		}
		if(finishedOrder != null)
			activeOrders.remove(finishedOrder);
	}

	/** Remove the trade from active orders */
	@Override
	public void notifyReject(final TradeInstruction instruction) {
		for(Iterator<ActiveOrder> iter = activeOrders.iterator(); iter.hasNext(); ) {
			if(iter.next().instruction == instruction) {
				iter.remove();
				break;
			}
		}
	}

	@Override
	public void warmup(ConsolidatedSnapshot snapshot, double[] forecasts) {
		final var totalPositionUsd = accountant.getPositionValueUsd(snapshot);
		for(Product product : Product.FAST_VALUES) {
			final var productIndex = product.getIndex();
			final var askPrice = snapshot.getProductSnapshot(product).askPrice;
			final var maxLong = totalPositionUsd * pctAllocation[productIndex] / askPrice;
			purchased[productIndex][purchasedIndex] = calcPurchase(maxLong, forecasts[productIndex]);
		}
		purchasedIndex = (purchasedIndex + 1) % lookback;
	}
}
