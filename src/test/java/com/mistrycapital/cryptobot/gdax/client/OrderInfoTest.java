package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.OrderType;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderInfoTest {
	private static final double EPSILON = 0.00000001;

	@Test
	void shouldCreateOrderInfo() {
		JsonParser parser = new JsonParser();
		JsonObject json = parser.parse("{\n" +
			"    \"id\": \"d0c5340b-6d6c-49d9-b567-48c4bfca13d2\",\n" +
			"    \"price\": \"0.10000000\",\n" +
			"    \"size\": \"0.01000000\",\n" +
			"    \"product_id\": \"BTC-USD\",\n" +
			"    \"side\": \"buy\",\n" +
			"    \"stp\": \"dc\",\n" +
			"    \"type\": \"limit\",\n" +
			"    \"time_in_force\": \"GTC\",\n" +
			"    \"post_only\": false,\n" +
			"    \"created_at\": \"2016-12-08T20:02:28.53864Z\",\n" +
			"    \"fill_fees\": \"0.0000000000000000\",\n" +
			"    \"filled_size\": \"0.00000000\",\n" +
			"    \"executed_value\": \"0.0000000000000000\",\n" +
			"    \"status\": \"pending\",\n" +
			"    \"settled\": false\n" +
			"}").getAsJsonObject();
		OrderInfo orderInfo = new OrderInfo(json);
		assertEquals("d0c5340b-6d6c-49d9-b567-48c4bfca13d2", orderInfo.getOrderId().toString());
		assertEquals(0.10, orderInfo.getPrice(), EPSILON);
		assertEquals(0.01, orderInfo.getSize(), EPSILON);
		assertEquals(Product.BTC_USD, orderInfo.getProduct());
		assertEquals(OrderSide.BUY, orderInfo.getOrderSide());
		assertEquals(SelfTradePreventionFlag.DC, orderInfo.getSelfTradePreventionFlag());
		assertEquals(OrderType.LIMIT, orderInfo.getType());
		assertEquals(TimeInForce.GTC, orderInfo.getTimeInForce());
		assertEquals(false, orderInfo.isPostOnly());
		assertEquals(1481227348538640L, orderInfo.getTimeMicros());
		assertEquals(0.0, orderInfo.getFillFees());
		assertEquals(0.0, orderInfo.getFilledSize());
		assertEquals(0.0, orderInfo.getExecutedValue());
		assertEquals(OrderStatus.PENDING, orderInfo.getStatus());
		assertEquals(false, orderInfo.isSettled());

		json = parser.parse("{\n" +
			"    \"id\": \"68e6a28f-ae28-4788-8d4f-5ab4e5e5ae08\",\n" +
			"    \"size\": \"1.00000000\",\n" +
			"    \"product_id\": \"BTC-USD\",\n" +
			"    \"side\": \"buy\",\n" +
			"    \"stp\": \"dc\",\n" +
			"    \"funds\": \"9.9750623400000000\",\n" +
			"    \"specified_funds\": \"10.0000000000000000\",\n" +
			"    \"type\": \"market\",\n" +
			"    \"post_only\": false,\n" +
			"    \"created_at\": \"2016-12-08T20:09:05.508883Z\",\n" +
			"    \"done_at\": \"2016-12-08T20:09:05.527Z\",\n" +
			"    \"done_reason\": \"filled\",\n" +
			"    \"fill_fees\": \"0.0249376391550000\",\n" +
			"    \"filled_size\": \"0.01291771\",\n" +
			"    \"executed_value\": \"9.9750556620000000\",\n" +
			"    \"status\": \"done\",\n" +
			"    \"settled\": true\n" +
			"}").getAsJsonObject();
		orderInfo = new OrderInfo(json);
		assertEquals("68e6a28f-ae28-4788-8d4f-5ab4e5e5ae08", orderInfo.getOrderId().toString());
		assertEquals(1.0, orderInfo.getSize(), EPSILON);
		assertEquals(Product.BTC_USD, orderInfo.getProduct());
		assertEquals(OrderSide.BUY, orderInfo.getOrderSide());
		assertEquals(SelfTradePreventionFlag.DC, orderInfo.getSelfTradePreventionFlag());
		assertEquals(9.97506234, orderInfo.getFunds(), EPSILON);
		assertEquals(10.0, orderInfo.getSpecifiedFunds(), EPSILON);
		assertEquals(OrderType.MARKET, orderInfo.getType());
		assertEquals(false, orderInfo.isPostOnly());
		assertEquals(1481227745508883L, orderInfo.getTimeMicros());
		assertEquals(1481227745527000L, orderInfo.getDoneMicros());
		assertEquals(Reason.FILLED, orderInfo.getDoneReason());
		assertEquals(0.0249376391550000, orderInfo.getFillFees());
		assertEquals(0.01291771, orderInfo.getFilledSize());
		assertEquals(9.9750556620000000, orderInfo.getExecutedValue());
		assertEquals(OrderStatus.DONE, orderInfo.getStatus());
		assertEquals(true, orderInfo.isSettled());
	}
}