package com.mistrycapital.cryptobot.execution;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;

public class TradeInstruction {
	/** Product to buy/sell */
	private final Product product;
	/** Amount of the crypto to buy/sell */
	private final double amount;
	/** Order side from our perspective - BUY means we are buying */
	private final OrderSide orderSide;
	/** Aggression - should we take or post passively */
	private final Aggression aggression;

	public TradeInstruction(final Product product, final double amount, final OrderSide orderSide, final Aggression aggression) {
		this.product = product;
		this.amount = amount;
		this.orderSide = orderSide;
		this.aggression = aggression;
	}

	/** @return Product to buy/sell */
	public final Product getProduct() {
		return product;
	}

	/** @return Amount of the crypto to buy/sell */
	public final double getAmount() {
		return amount;
	}

	/** @return Order side from our perspective - BUY means we are buying */
	public final OrderSide getOrderSide() {
		return orderSide;
	}

	/** @return Aggression - should we take or post passively */
	public final Aggression getAggression() {
		return aggression;
	}

	/** @return Description of instruction in text, e.g. "BUY 1.32 of BTC-USD using TAKE" */
	@Override
	public String toString() {
		return orderSide + " " + amount + " of " + product + " using " + aggression;
	}
}
