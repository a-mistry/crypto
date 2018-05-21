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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tactic that holds positions for two hours based on the forecast value
 */
public class TwoHourTactic implements Tactic {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final double EPSILON8 = 1e-8;

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final int lookback;
	private final double[][] purchased;
	private final TradeInstruction[][] instructions;
	private final double[] pctAllocation;
	private final double forecastThreshold;
	private final double tradeUsdThreshold;
	private final double tradeScaleFactor;

	private int purchasedIndex;

	public TwoHourTactic(MCProperties properties, TimeKeeper timeKeeper, Accountant accountant) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		lookback = 2 * 60 * 60 / intervalSeconds;
		purchased = new double[Product.count][lookback];
		instructions = new TradeInstruction[Product.count][lookback];
		purchasedIndex = 0;
		pctAllocation = new double[Product.count];
		final double defaultPctAllocation = properties.getDoubleProperty("tactic.pctAllocation.default");
		forecastThreshold = properties.getDoubleProperty("tactic.buyThreshold");
		tradeUsdThreshold = properties.getDoubleProperty("tactic.tradeUsdThreshold");
		tradeScaleFactor = properties.getDoubleProperty("tactic.tradeScaleFactor");
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			pctAllocation[productIndex] =
				properties.getDoubleProperty("tactic.pctAllocation." + product, defaultPctAllocation);
			log.debug("Two hour tactic " + product + " alloc " + (100 * pctAllocation[productIndex]) + "%");
		}
	}

	@Override
	public List<TradeInstruction> decideTrades(final ConsolidatedSnapshot snapshot, final double[] forecasts) {
		double totalPositionUsd = accountant.getPositionValueUsd(snapshot);
		List<TradeInstruction> trades = null;
		for(Product product : Product.FAST_VALUES) {
			final var productIndex = product.getIndex();
			final var askPrice = snapshot.getProductSnapshot(product).askPrice;

			final boolean purchaseNow =
				!Double.isNaN(forecasts[productIndex]) && forecasts[productIndex] > forecastThreshold;

			final var maxLongUsd = totalPositionUsd * pctAllocation[productIndex];
			final var targetProductPosition = calcTargetPosition(product, purchaseNow, maxLongUsd / askPrice);

			final var currentProductPositionUsd = accountant.getBalance(product.getCryptoCurrency());
			final var deltaPosition = targetProductPosition - currentProductPositionUsd;
			final var deltaPositionUsd = deltaPosition * askPrice;

			if(Math.abs(deltaPositionUsd) > tradeUsdThreshold) {
				if(trades == null)
					trades = new ArrayList<>(Product.count);
				final TradeInstruction instruction;
				if(deltaPosition > 0)
					instruction = new TradeInstruction(product, deltaPosition, OrderSide.BUY, Aggression.POST_ONLY);
				else
					instruction = new TradeInstruction(product, -deltaPosition, OrderSide.SELL, Aggression.POST_ONLY);
				trades.add(instruction);
				log.debug("Tactic decided to " + instruction.getOrderSide() + " " + instruction.getAmount() + " "
					+ product + " ask " + askPrice);
				instructions[productIndex][purchasedIndex] = instruction;
			} else {
				instructions[productIndex][purchasedIndex] = null;
			}
		}
		purchasedIndex = (purchasedIndex + 1) % purchased.length;
		return trades;
	}

	/** Note the amount purchased (negative if sold) for each instruction */
	@Override
	public void notifyFill(final TradeInstruction instruction, final Product product, final OrderSide orderSide,
		final double amount, final double price)
	{
		int productIndex = product.getIndex();
		final double[] purchasedProduct = purchased[productIndex];
		final TradeInstruction[] purchasedInstruction = instructions[productIndex];
		for(int i = 0; i < purchasedInstruction.length; i++)
			if(purchasedInstruction[i] == instruction) {
				purchasedProduct[i] += orderSide == OrderSide.BUY ? amount : -amount;
				if(Math.abs(purchasedProduct[i]) < EPSILON8) purchasedProduct[i] = 0.0;
				log.debug("Tactic recorded fill " + orderSide + " " + amount + " " + product + " at " + price);
			}
	}

	/**
	 * Calculates the target position in crypto given the purchase history, whether the forecast indicates to
	 * purchase now, and max position
	 *
	 * @param product     Product
	 * @param purchaseNow True if we want to purchase now
	 * @param maxLong     Maximum long position
	 * @return Crypto amount to be long
	 */
	private double calcTargetPosition(final Product product, final boolean purchaseNow, final double maxLong)
	{
		final double[] purchasedProduct = purchased[product.getIndex()];
		double positionCrypto = 0.0;
		for(double purchase : purchasedProduct)
			positionCrypto += purchase;
		if(purchaseNow)
			positionCrypto += maxLong * tradeScaleFactor / purchased.length;
		else
			positionCrypto -= purchasedProduct[purchasedIndex];
		return Math.max(0.0, Math.min(maxLong, positionCrypto));
	}
}
