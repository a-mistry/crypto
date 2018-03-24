package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class Open extends OrderGdaxMessage {
	/** Price */
	private final double price;
	/** Remaining size */
	private final double remainingSize;
	
	public Open(JsonObject json) {
		super(json);
		price = Double.parseDouble(json.get("price").getAsString());
		remainingSize = Double.parseDouble(json.get("remaining_size").getAsString());
	}

	@Override
	public final Type getType() {
		return Type.OPEN;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}

	public final double getPrice() {
		return price;
	}
	
	public final double getRemainingSize() {
		return remainingSize;
	}
}