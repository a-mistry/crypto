package com.mistrycapital.cryptobot.execution;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;

import static com.mistrycapital.cryptobot.gdax.client.GdaxClient.gdaxDecimalFormat;

public class TradeInstruction {
	/** Product to buy/sell */
	private final Product product;
	/** Amount of the crypto to buy/sell */
	private final double amount;
	/** Order side from our perspective - BUY means we are buying */
	private final OrderSide orderSide;
	/** Aggression - should we take or post passively */
	private final Aggression aggression;
	/** Forecast on the product (used for tracking) */
	private final double forecast;

	public TradeInstruction(final Product product, final double amount, final OrderSide orderSide,
		final Aggression aggression, final double forecast)
	{
		this.product = product;
		this.amount = amount;
		this.orderSide = orderSide;
		this.aggression = aggression;
		this.forecast = forecast;
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

	/** @return Forecast on the product (used for tracking) */
	public final double getForecast() {
		return forecast;
	}

	/** @return Description of instruction in text, e.g. "BUY 1.32 of BTC-USD using TAKE" */
	@Override
	public String toString() {
		return orderSide + " " + gdaxDecimalFormat.format(amount) + " of " + product + " using " + aggression +
			" with forecast " + forecast;
	}
}
