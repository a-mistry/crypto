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
import java.util.List;

public class TwoHourTactic implements Tactic {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final int lookback;
	private final boolean[][] purchased;
	private final double[] pctAllocation;
	private final double forecastThreshold;
	private final double tradeUsdThreshold;

	private int purchasedIndex;

	public TwoHourTactic(MCProperties properties, TimeKeeper timeKeeper, Accountant accountant) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		lookback = 2 * 60 * 60 / intervalSeconds;
		purchased = new boolean[Product.count][lookback];
		purchasedIndex = 0;
		pctAllocation = new double[Product.count];
		final double defaultPctAllocation = properties.getDoubleProperty("tactic.pctAllocation.default");
		forecastThreshold = properties.getDoubleProperty("tactic.buyThreshold");
		tradeUsdThreshold = properties.getDoubleProperty("tactic.tradeUsdThreshold");
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
			final var bidPrice = snapshot.getProductSnapshot(product).bidPrice;
			final var askPrice = snapshot.getProductSnapshot(product).askPrice;

			purchased[productIndex][purchasedIndex] = forecasts[productIndex] > forecastThreshold;

			final var allocationUsd = totalPositionUsd * pctAllocation[productIndex];
			final var percentLong = calcPercentLong(purchased[productIndex]);

			final var targetProductPositionUsd = allocationUsd * percentLong;
			final var currentProductPositionUsd = accountant.getBalance(product.getCryptoCurrency()) * askPrice;

			if(Math.abs(targetProductPositionUsd - currentProductPositionUsd) > tradeUsdThreshold) {
				final var targetProductPosition = Math.max(
					0.0,
					targetProductPositionUsd > currentProductPositionUsd
						? targetProductPositionUsd / askPrice : targetProductPositionUsd / bidPrice
				);
				final var deltaPosition = targetProductPosition - accountant.getBalance(product.getCryptoCurrency());
				if(trades == null)
					trades = new ArrayList<>(Product.count);
				final TradeInstruction instruction;
				if(deltaPosition > 0)
					instruction = new TradeInstruction(product, deltaPosition, OrderSide.BUY, Aggression.POST_ONLY);
				else
					instruction = new TradeInstruction(product, -deltaPosition, OrderSide.SELL, Aggression.POST_ONLY);
				trades.add(instruction);
			}
		}
		purchasedIndex = (purchasedIndex + 1) % purchased.length;
		return trades;
	}

	private double calcPercentLong(boolean[] purchaseEvaluations) {
		int count = 0;
		for(boolean isPurchase : purchaseEvaluations)
			if(isPurchase)
				count++;
		return Math.min(1.0, 5 * ((double) count) / purchaseEvaluations.length);
	}
}
