package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class Activate extends OrderGdaxMessage {
	/** Stop price */
	private final double stopPrice;
	/** True if size specified. If false, use funds */
	private final boolean hasSize;
	/** Size */
	private final double size;
	/** Funds */
	private final double funds;
	/** Stop type - loss or entry */
	private final StopType stopType;
	
	public Activate(JsonObject json) {
		super(json);
		stopPrice = Double.parseDouble(json.get("stop_price").getAsString());
		hasSize = json.has("size");
		if(hasSize) {
			size = Double.parseDouble(json.get("size").getAsString());
		} else {
			size = 0.0;
		}
		funds = json.has("funds") ? Double.parseDouble(json.get("funds").getAsString()) : 0.0;
		stopType = StopType.parse(json.get("stop_type").getAsString());
	}

	@Override
	public final Type getType() {
		return Type.ACTIVATE;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}
	
	public final double getStopPrice() {
		return stopPrice;
	}
	
	public final boolean hasSize() {
		return hasSize;
	}
	
	public final double getSize() {
		if(!hasSize) {
			throw new RuntimeException("Size retrieved but stop order did not specify size");
		}
		return size;
	}
	
	public final double getFunds() {
		return funds;
	}
	
	public final StopType getStopType() {
		return stopType;
	}
}