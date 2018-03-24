package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Book implements GdaxMessage {
	/** UTC time in microseconds */
	private final long timeMicros;
	/** Product */
	private final Product product;
	/** Sequence number */
	private final long sequence;
	/** Bids */
	private final Order[] bids;
	/** Asks */
	private final Order[] asks;
	
	public Book(final JsonObject json, final Product product) {
		this.product = product;
		timeMicros = System.currentTimeMillis()*1000L;
		sequence = json.get("sequence").getAsLong();
		bids = parseOrderArray(json.get("bids").getAsJsonArray());
		asks = parseOrderArray(json.get("asks").getAsJsonArray());
	}
	
	private Order[] parseOrderArray(final JsonArray orderArray) {
		Order[] orders = new Order[orderArray.size()];
		for(int i=0; i<orders.length; i++) {
			JsonArray order = orderArray.get(i).getAsJsonArray();
			double price = Double.parseDouble(order.get(0).getAsString());
			double size = Double.parseDouble(order.get(1).getAsString());
			UUID orderId = UUID.fromString(order.get(2).getAsString());
			orders[i] = new Order(orderId, price, size);
		}
		return orders;
	}

	@Override
	public Type getType() {
		return Type.BOOK;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}

	@Override
	public Product getProduct() {
		return product;
	}

	@Override
	public long getTimeMicros() {
		return timeMicros;
	}

	@Override
	public long getSequence() {
		return sequence;
	}
	
	public Order[] getBids() {
		return bids;
	}
	
	public Order[] getAsks() {
		return asks;
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
	
	public class Order {
		public final UUID orderId;
		public final double price;
		public final double size;
		
		Order(UUID orderId, double price, double size) {
			this.orderId = orderId;
			this.price = price;
			this.size = size;
		}
	}
}