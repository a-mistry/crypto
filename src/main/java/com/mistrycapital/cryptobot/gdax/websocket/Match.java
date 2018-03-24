package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.UUID;

import com.google.gson.JsonObject;

public class Match extends CommonGdaxMessage {
	/** Trade id */
	private final long tradeId;
	/** Order id of maker */
	private final UUID makerOrderId;
	/** Order id of taker */
	private final UUID takerOrderId;
	/** Price */
	private final double price;
	/** Size traded */
	private final double size;
	/** Order side */
	private final OrderSide side;

	Match(JsonObject json) {
		super(json);
		tradeId = json.get("trade_id").getAsLong();
		makerOrderId = UUID.fromString(json.get("maker_order_id").getAsString());
		takerOrderId = UUID.fromString(json.get("taker_order_id").getAsString());
		price = Double.parseDouble(json.get("price").getAsString());
		size = Double.parseDouble(json.get("size").getAsString());
		side = OrderSide.parse(json.get("side").getAsString());
	}

	@Override
	public final Type getType() {
		return Type.MATCH;
	}
	
	@Override
	public void process(final GdaxMessageProcessor processor) {
		processor.process(this);
	}
	
	public final long getTradeId() {
		return tradeId;
	}

	public final UUID getMakerOrderId() {
		return makerOrderId;
	}
	
	public final UUID getTakerOrderId() {
		return takerOrderId;
	}
	
	public final double getPrice() {
		return price;
	}
	
	public final double getSize() {
		return size;
	}
	
	public final OrderSide getOrderSide() {
		return side;
	}
}
