package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;

public class Unknown extends CommonGdaxMessage {
	Unknown(JsonObject json) {
		super(json);
	}

	@Override
	public Type getType() {
		return Type.UNKNOWN;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		throw new RuntimeException("Should not process an unknown message: " + toString());
	}
}