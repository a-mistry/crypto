package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.execution.Aggression;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.List;
import java.util.Random;

public class SimExecutionEngine implements ExecutionEngine {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Accountant accountant;
	private final Tactic tactic;
	private final double takeTransactionCost;
	private final double postFillRate;
	private final double[] postSlippage;
	private final double adverseFillPenalty;
	private final ConsolidatedHistory history;
	private final Random postFillRandomGen;

	private int tradeCount;

	SimExecutionEngine(MCProperties properties, Accountant accountant, Tactic tactic, ConsolidatedHistory history) {
		this.accountant = accountant;
		this.tactic = tactic;
		this.history = history;
		takeTransactionCost = properties.getDoubleProperty("sim.takeTransactionCostOnes", 0.0030);
		postFillRate = properties.getDoubleProperty("sim.postFillRate", 1.0);
		postSlippage = new double[Product.count];
		final var defaultPostSlippage = properties.getDoubleProperty("sim.postSlippage", 0.0010);
		for(Product product : Product.FAST_VALUES) {
			postSlippage[product.getIndex()] =
				properties.getDoubleProperty("sim.postSlippage." + product, defaultPostSlippage);
			log.info("Using post slippage " + postSlippage[product.getIndex()] + " for " + product);
		}
		adverseFillPenalty = properties.getDoubleProperty("sim.adverseFillPenalty", 0.10);
		postFillRandomGen = new Random(0);
	}

	@Override
	public void trade(final List<TradeInstruction> instructions) {
		ConsolidatedSnapshot snapshot = history.latest();

		for(TradeInstruction instruction : instructions) {
			final Product product = instruction.getProduct();
			final Currency cryptoCurrency = product.getCryptoCurrency();
			final double price = getImpactedPrice(instruction.getOrderSide(), instruction.getAmount(),
				snapshot.getProductSnapshot(product));
			if(Double.isNaN(price))
				continue; // no legitimate price; assume no fill

			// make sure not to trade more than we have available, regardless of the instructions
			final double dollars;
			final double crypto;
			double transactionCost = 0.0;
			boolean shouldFillOrder = false;
			if(instruction.getAggression() == Aggression.TAKE) {
				shouldFillOrder = true;
				transactionCost = takeTransactionCost;
			} else {
				// adverse fills when we are going in the same direction as the forecast
				final double fillRate = instruction.getOrderSide().sign() == Math.signum(instruction.getForecast())
					? postFillRate * (1 - adverseFillPenalty) : postFillRate * (1 + adverseFillPenalty);
				shouldFillOrder = postFillRandomGen.nextDouble() < fillRate;
				transactionCost = postSlippage[product.getIndex()];
			}
			if(shouldFillOrder) {
				// fill if a market order or otherwise target fill rate
				if(instruction.getOrderSide() == OrderSide.BUY) {
					dollars = Math.min(instruction.getAmount() * price, accountant.getAvailable(Currency.USD));
					crypto = dollars * (1 - transactionCost) / price;
					accountant.recordTrade(Currency.USD, -dollars, cryptoCurrency, crypto);
				} else {
					crypto = Math.min(instruction.getAmount(), accountant.getAvailable(cryptoCurrency));
					dollars = (1 - transactionCost) * crypto * price;
					accountant.recordTrade(Currency.USD, dollars, cryptoCurrency, -crypto);
				}
				tactic.notifyFill(instruction, product, instruction.getOrderSide(), crypto, price);
			}
			tradeCount++;
		}
	}

	double getImpactedPrice(final OrderSide orderSide, final double amount, final ProductSnapshot productSnapshot) {
		// TODO: unit test this
		// if we're taking less than the line, just return; otherwise, approximate with level info we have
		if(orderSide == OrderSide.BUY) {
			if(amount < productSnapshot.askSize)
				return productSnapshot.askPrice;
			else
				return getImpactedPrice(orderSide, amount, productSnapshot.askPrice, productSnapshot.askSize,
					productSnapshot.askSize1Pct, productSnapshot.askSize5Pct);
		} else {
			if(amount < productSnapshot.bidSize)
				return productSnapshot.bidPrice;
			else
				return getImpactedPrice(orderSide, amount, productSnapshot.bidPrice, productSnapshot.bidSize,
					productSnapshot.bidSize1Pct, productSnapshot.bidSize5Pct);

		}
	}

	private double getImpactedPrice(final OrderSide orderSide, final double amount, final double priceL0,
		final double sizeL0, final double size1Pct, final double size5Pct)
	{
		double priceXSize = 0.0;
		double remaining = amount;

		final double fillAmountL0 = Math.min(remaining, sizeL0);
		priceXSize += fillAmountL0 * priceL0;
		remaining -= fillAmountL0;

		if(remaining > 0) {
			// assume fill price is proportionally away from top of book
			final double fillAmount = Math.min(remaining, size1Pct - sizeL0);
			final double fillPriceDist = ((fillAmount + sizeL0) / size1Pct) * 0.01 * priceL0;
			final double fillPrice = orderSide == OrderSide.BUY ? priceL0 + fillPriceDist : priceL0 - fillPriceDist;
			priceXSize += fillAmount * fillPrice;
			remaining -= fillAmount;
		}

		if(remaining > 0) {
			final double fillAmount = Math.min(remaining, size5Pct - size1Pct);
			final double fillPriceDist = ((fillAmount + size1Pct) / size5Pct) * 0.05 * priceL0;
			final double fillPrice = orderSide == OrderSide.BUY ? priceL0 + fillPriceDist : priceL0 - fillPriceDist;
			priceXSize += fillAmount * fillPrice;
			remaining -= fillAmount;
		}

		if(remaining > 0) {
			// fill any remaining at 5% away
			final double fillPrice = orderSide == OrderSide.BUY ? priceL0 * 1.05 : priceL0 * 0.95;
			priceXSize += remaining * fillPrice;
		}

		return priceXSize / amount;
	}

	/** Used to count trading activity in sim */
	int getAndResetTradeCount() {
		int retValue = tradeCount;
		tradeCount = 0;
		return retValue;
	}
}
