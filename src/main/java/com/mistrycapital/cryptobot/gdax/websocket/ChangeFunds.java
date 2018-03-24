package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class ChangeFunds extends OrderGdaxMessage {
	/** Price */
	private final double price;
	/** Old funds */
	private final double oldFunds;
	/** New funds */
	private final double newFunds;

	ChangeFunds(JsonObject json) {
		super(json);
		if(json.has("price")) {
			price = Double.parseDouble(json.get("price").getAsString());
		} else {
			price = Double.NaN;
		}
		oldFunds = Double.parseDouble(json.get("old_funds").getAsString());
		newFunds = Double.parseDouble(json.get("new_funds").getAsString());
	}

	@Override
	public final Type getType() {
		return Type.CHANGE_FUNDS;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}

	public final double getPrice() {
		return price;
	}
	
	public final double getOldFunds() {
		return oldFunds;
	}
	
	public final double getNewFunds() {
		return newFunds;
	}
}