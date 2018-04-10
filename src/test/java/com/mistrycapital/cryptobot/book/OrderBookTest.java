package com.mistrycapital.cryptobot.book;

import static org.junit.jupiter.api.Assertions.*;

import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.util.UUID;

class OrderBookTest {
	private static final double EPSILON = 0.00000001;

	@Test
	void shouldCreateGdaxSnapshot() {
		TimeKeeper timeKeeper = new FakeTimeKeeper();
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

	@Test
	void shouldBuildBookAndReadLineData() {
		TimeKeeper timeKeeper = new FakeTimeKeeper();
		OrderBook book = new OrderBook(timeKeeper, Product.BTC_USD);
		GdaxMessageProcessor processor = book.getBookProcessor();

		JsonObject json = new JsonParser().parse("{\n" +
			"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
			"    \"product_id\": \"BCH-USD\",\n" +
			"    \"sequence\": 3,\n" +
			"    \"bids\": [\n" +
			"        [ \"295.96\",\"0.05088265\",\"3b0f1225-7f84-490b-a29f-0faef9de823a\" ],\n" +
			"        [ \"293.96\",\"1.05088265\",\"4b0f1225-7f84-490b-a29f-0faef9de823a\" ],\n" +
			"        [ \"290.96\",\"5.05088265\",\"5b0f1225-7f84-490b-a29f-0faef9de823a\" ]\n" +
			"    ],\n" +
			"    \"asks\": [\n" +
			"        [ \"295.97\",\"5.72036512\",\"da863862-25f4-4868-ac41-005d11ab0a5f\" ],\n" +
			"        [ \"296.97\",\"6.72036512\",\"aa863862-25f4-4868-ac41-005d11ab0a5f\" ]\n" +
			"    ]\n" +
			"}").getAsJsonObject();
		processor.process(new Book(json));

		BBO bbo = book.getBBO();
		assertEquals(295.96, bbo.bidPrice, EPSILON);
		assertEquals(295.97, bbo.askPrice, EPSILON);
		assertEquals(0.05088265, bbo.bidSize, EPSILON);
		assertEquals(5.72036512, bbo.askSize, EPSILON);

		assertEquals(3, book.getBidCount());
		assertEquals(2, book.getAskCount());
		assertEquals(0.05088265 * 3 + 6, book.getBidSize(), EPSILON);
		assertEquals(0.72036512 * 2 + 11, book.getAskSize(), EPSILON);

		assertEquals(2, book.getBidCountGEPrice(292.0));
		assertEquals(1, book.getAskCountLEPrice(296.0));
		assertEquals(0.05088265 * 2 + 1, book.getBidSizeGEPrice(292.0), EPSILON);
		assertEquals(5.72036512, book.getAskSizeLEPrice(296.0), EPSILON);

		// verify efficient depth calcs
		bbo = new BBO();
		Depth[] depths = new Depth[2];
		depths[0] = new Depth();
		depths[0].pctFromMid = 296.0 / 295.965 - 1.0;
		depths[1] = new Depth();
		depths[1].pctFromMid = 1.0 - 292.0 / 295.965;
		book.recordDepthsAndBBO(bbo, depths);

		assertEquals(295.96, bbo.bidPrice, EPSILON);
		assertEquals(295.97, bbo.askPrice, EPSILON);
		assertEquals(0.05088265, bbo.bidSize, EPSILON);
		assertEquals(5.72036512, bbo.askSize, EPSILON);

		assertEquals(1, depths[0].bidCount);
		assertEquals(1, depths[0].askCount);
		assertEquals(0.05088265, depths[0].bidSize, EPSILON);
		assertEquals(5.72036512, depths[0].askSize, EPSILON);
		assertEquals(2, depths[1].bidCount);
		assertEquals(2, depths[1].askCount);
		assertEquals(0.05088265 * 2 + 1, depths[1].bidSize, EPSILON);
		assertEquals(0.72036512 * 2 + 11, depths[1].askSize, EPSILON);
	}

	@Test
	void shouldModifyBook() {
		TimeKeeper timeKeeper = new FakeTimeKeeper();
		OrderBook book = new OrderBook(timeKeeper, Product.BTC_USD);
		GdaxMessageProcessor processor = book.getBookProcessor();

		UUID orderId1 = UUID.randomUUID();
		UUID orderId2 = UUID.randomUUID();
		UUID orderId3 = UUID.randomUUID();
		UUID orderId4 = UUID.randomUUID();

		JsonObject msgJson = new JsonObject();
		msgJson.addProperty("side", "buy");
		msgJson.addProperty("product_id", "BTC-USD");
		msgJson.addProperty("time", "2014-11-07T08:19:27.028459Z");
		msgJson.addProperty("sequence", 10L);
		msgJson.addProperty("order_id", orderId1.toString());
		msgJson.addProperty("price", 20.0);
		msgJson.addProperty("remaining_size", 1.0);
		processor.process(new Open(msgJson));

		msgJson.addProperty("side", "sell");
		msgJson.addProperty("order_id", orderId2.toString());
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 2.0);
		processor.process(new Open(msgJson));
		msgJson.addProperty("order_id", orderId3.toString());
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 0.5);
		processor.process(new Open(msgJson));
		msgJson.addProperty("order_id", orderId4.toString());
		msgJson.addProperty("price", 23.0);
		msgJson.addProperty("remaining_size", 3.14);
		processor.process(new Open(msgJson));

		BBO bbo = book.getBBO();
		assertEquals(20.0, bbo.bidPrice, EPSILON);
		assertEquals(22.0, bbo.askPrice, EPSILON);
		assertEquals(1.0, bbo.bidSize, EPSILON);
		assertEquals(2.5, bbo.askSize, EPSILON);

		// modify size on top of book
		msgJson = new JsonObject();
		msgJson.addProperty("side", "sell");
		msgJson.addProperty("product_id", "BTC-USD");
		msgJson.addProperty("time", "2014-11-07T08:19:27.028459Z");
		msgJson.addProperty("sequence", 10L);
		msgJson.addProperty("order_id", orderId2.toString());
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("old_size", 2.0);
		msgJson.addProperty("new_size", 3.0);
		processor.process(new ChangeSize(msgJson));

		bbo = new BBO();
		book.recordBBO(bbo);
		assertEquals(20.0, bbo.bidPrice, EPSILON);
		assertEquals(22.0, bbo.askPrice, EPSILON);
		assertEquals(1.0, bbo.bidSize, EPSILON);
		assertEquals(3.5, bbo.askSize, EPSILON);
		assertEquals(21.0, bbo.midPrice(), EPSILON);
		assertEquals(3.5+3.14, book.getAskSize(), EPSILON);

		// fill order
		msgJson = new JsonObject();
		msgJson.addProperty("side", "sell");
		msgJson.addProperty("product_id", "BTC-USD");
		msgJson.addProperty("time", "2014-11-07T08:19:27.028459Z");
		msgJson.addProperty("sequence", 10L);
		msgJson.addProperty("order_id", orderId2.toString());
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 0.0);
		msgJson.addProperty("reason", "filled");
		processor.process(new Done(msgJson));

		book.recordBBO(bbo);
		assertEquals(20.0, bbo.bidPrice, EPSILON);
		assertEquals(22.0, bbo.askPrice, EPSILON);
		assertEquals(1.0, bbo.bidSize, EPSILON);
		assertEquals(0.5, bbo.askSize, EPSILON);
		assertEquals(21.0, bbo.midPrice(), EPSILON);
		assertEquals(0.5+3.14, book.getAskSize(), EPSILON);

		// cancel order
		msgJson.addProperty("order_id", orderId3.toString());
		msgJson.addProperty("remaining_size", 0.5);
		msgJson.addProperty("reason", "canceled");
		processor.process(new Done(msgJson));

		book.recordBBO(bbo);
		assertEquals(20.0, bbo.bidPrice, EPSILON);
		assertEquals(23.0, bbo.askPrice, EPSILON);
		assertEquals(1.0, bbo.bidSize, EPSILON);
		assertEquals(3.14, bbo.askSize, EPSILON);
		assertEquals(21.5, bbo.midPrice(), EPSILON);
		assertEquals(3.14, book.getAskSize(), EPSILON);
	}
}