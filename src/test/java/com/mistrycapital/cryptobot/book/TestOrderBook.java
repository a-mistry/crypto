package com.mistrycapital.cryptobot.book;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessageProcessor;
import com.mistrycapital.cryptobot.gdax.websocket.Open;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.time.Instant;
import java.util.UUID;

class TestOrderBook {
	private static final double EPSILON = 0.00000001;
	
	@BeforeEach
	void setUp() throws Exception {
	}

	@Test
	void shouldCreateGdaxSnapshot() {
		TimeKeeper timeKeeper = new FakeTimeKeeper(System.currentTimeMillis());
		OrderBook book = new OrderBook(timeKeeper, Product.BTC_USD);
		GdaxMessageProcessor processor = book.getBookProcessor();
		
		UUID orderId1 = UUID.randomUUID();
		UUID orderId2 = UUID.randomUUID();
		UUID orderId3 = UUID.randomUUID();
		
		JsonObject msgJson = new JsonObject();
		msgJson.addProperty("side", "buy");
		msgJson.addProperty("product_id", "BTC-USD");
		msgJson.addProperty("time", "2014-11-07T08:19:27.028459Z");
		msgJson.addProperty("sequence", 10L);
		msgJson.addProperty("order_id", orderId1.toString());
		msgJson.addProperty("price", 20.0);
		msgJson.addProperty("remaining_size", 1.0);
		processor.process(new Open(msgJson));
		
		msgJson.addProperty("order_id", orderId2.toString());
		msgJson.addProperty("price", 18.0);
		msgJson.addProperty("remaining_size", 0.5);
		processor.process(new Open(msgJson));
		
		msgJson.addProperty("order_id", orderId3.toString());
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 1.75);
		msgJson.addProperty("side", "sell");
		processor.process(new Open(msgJson));
		
		String result = book.getGdaxSnapshot();
		JsonObject json = new JsonParser().parse(result).getAsJsonObject();
		assertEquals(timeKeeper.iso8601(), json.get("time").getAsString());
		assertEquals("BTC-USD", json.get("product_id").getAsString());
		assertEquals(10L, json.get("sequence").getAsLong());
		JsonArray bids = json.get("bids").getAsJsonArray();
		assertEquals(2, bids.size());
		JsonArray order = bids.get(0).getAsJsonArray();
		if(order.get(2).getAsString().equals(orderId2.toString())) {
			order = bids.get(1).getAsJsonArray();
		}
		assertEquals(20.0, order.get(0).getAsDouble(), EPSILON);
		assertEquals(1.0, order.get(1).getAsDouble(), EPSILON);
		assertEquals(orderId1.toString(), order.get(2).getAsString());
		order = bids.get(1).getAsJsonArray();
		if(order.get(2).getAsString().equals(orderId1.toString())) {
			order = bids.get(0).getAsJsonArray();
		}
		assertEquals(18.0, order.get(0).getAsDouble(), EPSILON);
		assertEquals(0.5, order.get(1).getAsDouble(), EPSILON);
		assertEquals(orderId2.toString(), order.get(2).getAsString());
		JsonArray asks = json.get("asks").getAsJsonArray();
		assertEquals(1, asks.size());
		order = asks.get(0).getAsJsonArray();
		assertEquals(22.0, order.get(0).getAsDouble(), EPSILON);
		assertEquals(1.75, order.get(1).getAsDouble(), EPSILON);
		assertEquals(orderId3.toString(), order.get(2).getAsString());
	}
}