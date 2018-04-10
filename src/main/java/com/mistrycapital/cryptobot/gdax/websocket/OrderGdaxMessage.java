package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;

abstract class OrderGdaxMessage extends CommonGdaxMessage {
	/** Order id */
	protected final UUID orderId;
	/** Order side */
	protected final OrderSide side;
	
	OrderGdaxMessage(JsonObject json) {
		super(json);
		orderId = UUID.fromString(json.get("order_id").getAsString());
		side = OrderSide.parse(json.get("side").getAsString());
	}

	public final UUID getOrderId() {
		return orderId;
	}
	
	public final OrderSide getOrderSide() {
		return side;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(getType().toString());
		builder.append(" message received at ");
		builder.append(timeMicros);
		builder.append(" sequence ");
		builder.append(sequence);
		builder.append(" for ");
		builder.append(product);
		builder.append(" order ");
		builder.append(orderId);
		builder.append(' ');
		builder.append(side);
		return builder.toString();
	}
}
