package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.common.OrderType;

import java.util.UUID;

public class Received extends OrderGdaxMessage {
	/** Price */
	private final double price;
	/** Size */
	private final double size;
	/** Funds (for some market orders) */
	private final double funds;
	/** Limit or market order */
	private final OrderType orderType;
	/** Client oid, if exists, null otherwise */
	private final UUID clientOid;

	public Received(JsonObject json) {
		super(json);
		price = json.has("price") ? Double.parseDouble(json.get("price").getAsString()) : Double.NaN;
		size = json.has("size") ? Double.parseDouble(json.get("size").getAsString()) : Double.NaN;
		funds = json.has("funds") ? Double.parseDouble(json.get("funds").getAsString()) : Double.NaN;
		orderType = OrderType.parse(json.get("order_type").getAsString());
		clientOid = json.has("client_oid") ? UUID.fromString(json.get("client_oid").getAsString()) : null;
	}

	@Override
	public Type getType() {
		return Type.RECEIVED;
	}

	@Override
	public void process(final GdaxMessageProcessor processor) {
		//processor.process(this);
	}

	/** Price */
	public final double getPrice() {
		return price;
	}

	/** Size */
	public final double getSize() {
		return size;
	}

	/** Funds (for some market orders) */
	public final double getFunds() {
		return funds;
	}

	/** Limit or market order */
	public final OrderType getOrderType() {
		return orderType;
	}

	/** Client oid, if exists, null otherwise */
	public final UUID getClientOid() {
		return clientOid;
	}
}
