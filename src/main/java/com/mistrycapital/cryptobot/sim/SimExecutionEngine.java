package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.List;

public class SimExecutionEngine implements ExecutionEngine {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Accountant accountant;
	private final double transactionCost;
	private final ConsolidatedHistory history;

	SimExecutionEngine(MCProperties properties, Accountant accountant, ConsolidatedHistory history) {
		this.accountant = accountant;
		this.history = history;
		transactionCost = properties.getDoubleProperty("sim.transactionCostOnes", 0.0030);
	}

	@Override
	public void trade(final List<TradeInstruction> instructions) {
		ConsolidatedSnapshot snapshot = history.latest();

		for(TradeInstruction instruction : instructions) {
			final Product product = instruction.getProduct();
			final Currency cryptoCurrency = product.getCryptoCurrency();

			// make sure not to trade more than we have available, regardless of the instructions
			if(instruction.getOrderSide() == OrderSide.BUY) {
				final double price = snapshot.getProductSnapshot(product).askPrice;
				final double dollars = Math.min(instruction.getAmount() * price, accountant.getAvailable(Currency.USD));
				final double crypto = dollars * (1 - transactionCost) / price;
				accountant.recordTrade(Currency.USD, -dollars, cryptoCurrency, crypto);
			} else {
				final double crypto = Math.min(instruction.getAmount(), accountant.getAvailable(cryptoCurrency));
				final double price = snapshot.getProductSnapshot(product).bidPrice;
				final double dollars = (1 - transactionCost) * crypto * price;
				accountant.recordTrade(Currency.USD, dollars, cryptoCurrency, -crypto);
			}
		}
	}
}
