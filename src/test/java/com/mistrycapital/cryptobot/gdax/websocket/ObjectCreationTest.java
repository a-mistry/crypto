package com.mistrycapital.cryptobot.gdax.websocket;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.appender.GdaxMessageAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage.Type;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;

class ObjectCreationTest {
	private static final double EPSILON = 0.00000001;

	private GdaxWebSocket webSocket;

	@BeforeEach
	void setUp()
		throws Exception
	{
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);

		TimeKeeper timeKeeper = new FakeTimeKeeper();
		GdaxMessageAppender fileAppender =
			new GdaxMessageAppender(logDir, "orders", "json", timeKeeper, new OrderBookManager(timeKeeper));
		webSocket = new GdaxWebSocket(timeKeeper, fileAppender);
	}

	@Test
	void shouldParseTime() {
		assertEquals(1415348368464459L, CommonGdaxMessage.parseTimeMicros("2014-11-07T08:19:28.464459Z"));
		assertEquals(1415348368000000L, CommonGdaxMessage.parseTimeMicros("2014-11-07T08:19:28Z"));
	}

	@Test
	void shouldCreateMessages() {
		verifyCorrectObject("{\n" +
				"    \"type\": \"open\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"sequence\": 10,\n" +
				"    \"order_id\": \"d50ec984-77a8-460a-b958-66f114b0de9b\",\n" +
				"    \"price\": \"200.2\",\n" +
				"    \"remaining_size\": \"1.00\",\n" +
				"    \"side\": \"sell\"\n" +
				"}",
			Open.class,
			open -> {
				assertEquals(Type.OPEN, open.getType());
				assertEquals(1415348367028459L, open.getTimeMicros());
				assertEquals(Product.BTC_USD, open.getProduct());
				assertEquals(10L, open.getSequence());
				assertEquals(UUID.fromString("d50ec984-77a8-460a-b958-66f114b0de9b"), open.getOrderId());
				assertEquals(200.2, open.getPrice(), EPSILON);
				assertEquals(1.0, open.getRemainingSize(), EPSILON);
				assertEquals(OrderSide.SELL, open.getOrderSide());
			});

		verifyCorrectObject("{\n" +
				"    \"type\": \"done\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"sequence\": 10,\n" +
				"    \"price\": \"200.2\",\n" +
				"    \"order_id\": \"d50ec984-77a8-460a-b958-66f114b0de9b\",\n" +
				"    \"reason\": \"filled\",\n" +
				"    \"side\": \"sell\",\n" +
				"    \"remaining_size\": \"0\"\n" +
				"}",
			Done.class,
			done -> {
				assertEquals(Type.DONE, done.getType());
				assertEquals(1415348367028459L, done.getTimeMicros());
				assertEquals(Product.BTC_USD, done.getProduct());
				assertEquals(10L, done.getSequence());
				assertEquals(200.2, done.getPrice(), EPSILON);
				assertEquals(UUID.fromString("d50ec984-77a8-460a-b958-66f114b0de9b"), done.getOrderId());
				assertEquals(Reason.FILLED, done.getReason());
				assertEquals(OrderSide.SELL, done.getOrderSide());
				assertEquals(0, done.getRemainingSize(), EPSILON);
			});

		verifyCorrectObject("{\n" +
				"    \"type\": \"match\",\n" +
				"    \"trade_id\": 10,\n" +
				"    \"sequence\": 50,\n" +
				"    \"maker_order_id\": \"ac928c66-ca53-498f-9c13-a110027a60e8\",\n" +
				"    \"taker_order_id\": \"132fb6ae-456b-4654-b4e0-d681ac05cea1\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"size\": \"5.23512\",\n" +
				"    \"price\": \"400.23\",\n" +
				"    \"side\": \"sell\"\n" +
				"}",
			Match.class,
			match -> {
				assertEquals(Type.MATCH, match.getType());
				assertEquals(10L, match.getTradeId());
				assertEquals(50L, match.getSequence());
				assertEquals(UUID.fromString("ac928c66-ca53-498f-9c13-a110027a60e8"), match.getMakerOrderId());
				assertEquals(UUID.fromString("132fb6ae-456b-4654-b4e0-d681ac05cea1"), match.getTakerOrderId());
				assertEquals(1415348367028459L, match.getTimeMicros());
				assertEquals(Product.BTC_USD, match.getProduct());
				assertEquals(5.23512, match.getSize(), EPSILON);
				assertEquals(400.23, match.getPrice(), EPSILON);
				assertEquals(OrderSide.SELL, match.getOrderSide());
			});

		verifyCorrectObject("{\n" +
				"    \"type\": \"change\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"sequence\": 80,\n" +
				"    \"order_id\": \"ac928c66-ca53-498f-9c13-a110027a60e8\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"new_size\": \"5.23512\",\n" +
				"    \"old_size\": \"12.234412\",\n" +
				"    \"price\": \"400.23\",\n" +
				"    \"side\": \"sell\"\n" +
				"}",
			ChangeSize.class,
			change -> {
				assertEquals(Type.CHANGE_SIZE, change.getType());
				assertEquals(1415348367028459L, change.getTimeMicros());
				assertEquals(80L, change.getSequence());
				assertEquals(UUID.fromString("ac928c66-ca53-498f-9c13-a110027a60e8"), change.getOrderId());
				assertEquals(Product.BTC_USD, change.getProduct());
				assertEquals(5.23512, change.getNewSize(), EPSILON);
				assertEquals(12.234412, change.getOldSize(), EPSILON);
				assertEquals(400.23, change.getPrice(), EPSILON);
				assertEquals(OrderSide.SELL, change.getOrderSide());
			});

		verifyCorrectObject("{\n" +
				"    \"type\": \"change\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"sequence\": 80,\n" +
				"    \"order_id\": \"ac928c66-ca53-498f-9c13-a110027a60e8\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"new_funds\": \"5.23512\",\n" +
				"    \"old_funds\": \"12.234412\",\n" +
				"    \"price\": \"400.23\",\n" +
				"    \"side\": \"sell\"\n" +
				"}",
			ChangeFunds.class,
			change -> {
				assertEquals(Type.CHANGE_FUNDS, change.getType());
				assertEquals(1415348367028459L, change.getTimeMicros());
				assertEquals(80L, change.getSequence());
				assertEquals(UUID.fromString("ac928c66-ca53-498f-9c13-a110027a60e8"), change.getOrderId());
				assertEquals(Product.BTC_USD, change.getProduct());
				assertEquals(5.23512, change.getNewFunds(), EPSILON);
				assertEquals(12.234412, change.getOldFunds(), EPSILON);
				assertEquals(400.23, change.getPrice(), EPSILON);
				assertEquals(OrderSide.SELL, change.getOrderSide());
			});

		verifyCorrectObject("{\n" +
				"  \"type\": \"activate\",\n" +
				"  \"product_id\": \"LTC-USD\",\n" +
				"  \"sequence\": 80,\n" +
				"  \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"  \"user_id\": \"12\",\n" +
				"  \"profile_id\": \"30000727-d308-cf50-7b1c-c06deb1934fc\",\n" +
				"  \"order_id\": \"7b52009b-64fd-0a2a-49e6-d8a939753077\",\n" +
				"  \"stop_type\": \"entry\",\n" +
				"  \"side\": \"buy\",\n" +
				"  \"stop_price\": \"80\",\n" +
				"  \"size\": \"2\",\n" +
				"  \"funds\": \"50\",\n" +
				"  \"taker_fee_rate\": \"0.0025\",\n" +
				"  \"private\": true\n" +
				"}",
			Activate.class,
			activate -> {
				assertEquals(Type.ACTIVATE, activate.getType());
				assertEquals(Product.LTC_USD, activate.getProduct());
				assertEquals(80L, activate.getSequence());
				assertEquals(1415348367028459L, activate.getTimeMicros());
				assertEquals(UUID.fromString("7b52009b-64fd-0a2a-49e6-d8a939753077"), activate.getOrderId());
				assertEquals(StopType.ENTRY, activate.getStopType());
				assertEquals(OrderSide.BUY, activate.getOrderSide());
				assertEquals(80.0, activate.getStopPrice(), EPSILON);
				assertEquals(2.0, activate.getSize(), EPSILON);
				assertEquals(50.0, activate.getFunds(), EPSILON);
			});

		verifyCorrectObject("{\n" +
				"    \"type\": \"received\",\n" +
				"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
				"    \"product_id\": \"BTC-USD\",\n" +
				"    \"sequence\": 10,\n" +
				"    \"order_id\": \"d50ec984-77a8-460a-b958-66f114b0de9b\",\n" +
				"    \"size\": \"1.34\",\n" +
				"    \"price\": \"502.1\",\n" +
				"    \"side\": \"buy\",\n" +
				"    \"order_type\": \"limit\"\n" +
				"}",
			Unknown.class,
			unknown -> {
				assertEquals(Type.UNKNOWN, unknown.getType());
				assertEquals(Product.BTC_USD, unknown.getProduct());
				assertEquals(10L, unknown.getSequence());
				assertEquals(1415348367028459L, unknown.getTimeMicros());
			});
	}

	@SuppressWarnings("unchecked")
	private <T extends GdaxMessage> void verifyCorrectObject(String json, Class<T> clazz, Consumer<T> verifier) {
		GdaxMessage msg = webSocket.parseMessage(json);
		assertTrue(msg.getClass().isAssignableFrom(clazz));
		verifier.accept((T) msg);
	}

	@Test
	void shouldCreateBook() {
		JsonObject json = new JsonParser().parse("{\n" +
			"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
			"    \"product_id\": \"BCH-USD\",\n" +
			"    \"sequence\": 3,\n" +
			"    \"bids\": [\n" +
			"        [ \"295.96\",\"0.05088265\",\"3b0f1225-7f84-490b-a29f-0faef9de823a\" ]\n" +
			"    ],\n" +
			"    \"asks\": [\n" +
			"        [ \"295.97\",\"5.72036512\",\"da863862-25f4-4868-ac41-005d11ab0a5f\" ],\n" +
			"        [ \"296.97\",\"6.72036512\",\"aa863862-25f4-4868-ac41-005d11ab0a5f\" ]\n" +
			"    ]\n" +
			"}").getAsJsonObject();
		Book book = new Book(json);
		assertEquals(Type.BOOK, book.getType());
		assertEquals(Product.BCH_USD, book.getProduct());
		assertEquals(1415348367028459L, book.getTimeMicros());
		assertEquals(3L, book.getSequence());
		Book.Order[] orders = book.getBids();
		assertEquals(1, orders.length);
		assertEquals(UUID.fromString("3b0f1225-7f84-490b-a29f-0faef9de823a"), orders[0].orderId);
		assertEquals(295.96, orders[0].price, EPSILON);
		assertEquals(0.05088265, orders[0].size, EPSILON);
		orders = book.getAsks();
		assertEquals(2, orders.length);
		assertEquals(UUID.fromString("da863862-25f4-4868-ac41-005d11ab0a5f"), orders[0].orderId);
		assertEquals(295.97, orders[0].price, EPSILON);
		assertEquals(5.72036512, orders[0].size, EPSILON);
		assertEquals(UUID.fromString("aa863862-25f4-4868-ac41-005d11ab0a5f"), orders[1].orderId);
		assertEquals(296.97, orders[1].price, EPSILON);
		assertEquals(6.72036512, orders[1].size, EPSILON);
	}
}