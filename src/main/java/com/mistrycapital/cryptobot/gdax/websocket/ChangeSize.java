package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class ChangeSize extends OrderGdaxMessage {
	/** Price */
	private final double price;
	/** Old size */
	private final double oldSize;
	/** New size */
	private final double newSize;

	public ChangeSize(JsonObject json) {
		super(json);
		if(json.has("price")) {
			price = Double.parseDouble(json.get("price").getAsString());
		} else {
			price = Double.NaN;
		}
		oldSize = Double.parseDouble(json.get("old_size").getAsString());
		newSize = Double.parseDouble(json.get("new_size").getAsString());
	}

	@Override
	public final Type getType() {
		return Type.CHANGE_SIZE;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}

	public final double getPrice() {
		return price;
	}
	
	public final double getOldSize() {
		return oldSize;
	}
	
	public final double getNewSize() {
		return newSize;
	}
}