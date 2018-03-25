package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Book extends CommonGdaxMessage {
	/** Bids */
	private final Order[] bids;
	/** Asks */
	private final Order[] asks;
	
	public Book(final JsonObject json) {
		super(json);
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

	public Order[] getBids() {
		return bids;
	}
	
	public Order[] getAsks() {
		return asks;
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