package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCProperties;

import java.util.List;

public class SimExecutionEngine implements ExecutionEngine {
	private static final double TRANSACTION_COST = MCProperties.getDoubleProperty("sim.transactionCostOnes", 0.0030);

	private final Accountant accountant;
	private ConsolidatedSnapshot snapshot;

	SimExecutionEngine(Accountant accountant) {
		this.accountant = accountant;
	}

	/** Store the given snapshot and use the quotes for fill prices */
	public void useSnapshot(ConsolidatedSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public void trade(final List<TradeInstruction> instructions) {
		for(TradeInstruction instruction : instructions) {
			final Product product = instruction.getProduct();

			if(instruction.getOrderSide() == OrderSide.BUY) {
				final double dollars = accountant.getAvailable(Currency.USD);
				final double price = snapshot.getProductSnapshot(product).askPrice;
				final double crypto = (1 - TRANSACTION_COST) * dollars / price;
				accountant.recordTrade(Currency.USD, -dollars, product.getCryptoCurrency(), crypto);
			} else {
				final Currency cryptoCurrency = product.getCryptoCurrency();
				final double crypto = accountant.getAvailable(cryptoCurrency);
				final double price = snapshot.getProductSnapshot(product).bidPrice;
				final double dollars = (1 - TRANSACTION_COST) * crypto * price;
				accountant.recordTrade(Currency.USD, dollars, cryptoCurrency, -crypto);
			}
		}
	}
}
