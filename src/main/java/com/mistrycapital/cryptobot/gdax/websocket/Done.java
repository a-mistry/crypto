package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class Done extends OrderGdaxMessage {
	/** True if limit order, false if market */
	private boolean isLimitOrder;
	/** Price */
	private final double price;
	/** Remaining size (for limit orders) */
	private final double remainingSize;
	/** Reason done - cancelled or filled */
	private final Reason reason;

	Done(JsonObject json) {
		super(json);
		isLimitOrder = json.has("price");
		if(isLimitOrder) {
			price = Double.parseDouble(json.get("price").getAsString());
			remainingSize = Double.parseDouble(json.get("remaining_size").getAsString());
		} else {
			price = 0.0;
			remainingSize = 0.0;
		}
		reason = Reason.valueOf(json.get("reason").getAsString().toUpperCase());
	}

	@Override
	public final Type getType() {
		return Type.DONE;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}
	
	public final boolean isLimitOrder() {
		return isLimitOrder;
	}

	public final double getPrice() {
		if(!isLimitOrder) {
			throw new RuntimeException("Tried to get price of market order");
		}
		return price;
	}
	
	public final double getRemainingSize() {
		if(!isLimitOrder) {
			throw new RuntimeException("Tried to get price of market order");
		}
		return remainingSize;
	}
	
	public final Reason getReason() {
		return reason;
	}
}