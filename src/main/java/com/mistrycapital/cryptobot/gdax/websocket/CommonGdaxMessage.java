package com.mistrycapital.cryptobot.gdax.websocket;

import java.time.Instant;

import com.google.gson.JsonObject;

abstract class CommonGdaxMessage implements GdaxMessage {
	/** UTC time in microseconds */
	protected final long timeMicros;
	/** Product */
	protected final Product product;
	/** Sequence number */
	protected final long sequence;
	
	CommonGdaxMessage(JsonObject json) {
		timeMicros = parseTimeMicros(json.get("time").getAsString());
		product = Product.parse(json.get("product_id").getAsString());
		sequence = json.get("sequence").getAsLong();
	}
	
	static final long parseTimeMicros(String timeString) {
		Instant instant = Instant.parse(timeString);
		return instant.getEpochSecond()*1000000 + instant.getNano()/1000;
	}
	
	@Override
	public final Product getProduct() {
		return product;
	}
	
	@Override
	public final long getTimeMicros() {
		return timeMicros;
	}
	
	@Override
	public final long getSequence() {
		return sequence;
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
		return builder.toString();
	}
}