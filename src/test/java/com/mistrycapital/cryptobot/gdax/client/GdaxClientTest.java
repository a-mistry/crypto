package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient.GdaxException;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.OrderType;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GdaxClientTest {
	private static final double EPSILON = 0.00000001;

	private static GdaxClient gdaxClient;

	@BeforeAll
	static void setUp()
		throws Exception
	{
		MCProperties properties = new MCProperties();
		String apiKey = properties.getProperty("gdax.client.apiKey");
		String apiSecret = properties.getProperty("gdax.client.apiSecret");
		String passPhrase = properties.getProperty("gdax.client.passPhrase");
		URI sandboxURI = new URI("https://api-public.sandbox.gdax.com");
		gdaxClient = new GdaxClient(sandboxURI, apiKey, apiSecret, passPhrase);
	}

	/**
	 * Print balances to stdout and return dollars
	 *
	 * @return USD balance
	 */
	private double listBalances()
		throws ExecutionException, InterruptedException
	{
		System.out.println("Currency Balances");
		var accounts = gdaxClient.getAccounts().get();
		accounts.stream()
			.forEach(account -> System.out.println(account.getCurrency() + " " + account.getAvailable()));

		return accounts.stream()
			.filter(account -> account.getCurrency() == Currency.USD)
			.findAny()
			.get()
			.getAvailable();
	}

	private OrderInfo waitForOrderComplete(UUID orderId)
		throws ExecutionException, InterruptedException
	{
		for(int i = 0; i < 10; i++) {
			var orderInfo = gdaxClient.getOrder(orderId).get();
			if(orderInfo.getStatus() == OrderStatus.DONE)
				return orderInfo;
			Thread.sleep(1000);
		}
		fail("Checked order info 10 times but it was still not done");
		return null;
	}

	@Test
	void shouldPlaceMarketOrder()
		throws Exception
	{
		// get dollar balance
		System.out.println("Starting balances");
		double dollars = listBalances();

		// nothing to test if balance too low
		if(dollars < 10) {
			System.err.println("Not testing market orders - sandbox balance is too low");
			return;
		}

		// First test is to buy $10 of ETH specifying funds
		var orderFuture =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 10);
		var orderInfo = orderFuture.get();
		UUID orderId = orderInfo.getOrderId();

		assertEquals(Product.ETH_USD, orderInfo.getProduct());
		assertEquals(OrderSide.BUY, orderInfo.getOrderSide());
		assertEquals(OrderType.MARKET, orderInfo.getType());
		assertEquals(TimeInForce.GTC, orderInfo.getTimeInForce());
		assertEquals(SelfTradePreventionFlag.DC, orderInfo.getSelfTradePreventionFlag());
		assertFalse(orderInfo.isPostOnly());

		System.out.println("Initial order info");
		System.out.println(orderInfo);

		orderInfo = waitForOrderComplete(orderId);

		System.out.println("Completed order info");
		System.out.println(orderInfo);
		double ethFilled = orderInfo.getFilledSize();

		double dollarsAfterPurchase = listBalances();
		assertEquals(dollarsAfterPurchase, dollars - orderInfo.getExecutedValue() - orderInfo.getFillFees(), EPSILON);
		assertEquals(0.0030, orderInfo.getFillFees() / orderInfo.getExecutedValue(), EPSILON);

		// Now sell the ETH we just bought using amount (similar to actual execution)
		orderInfo =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.SELL, MarketOrderSizingType.SIZE, ethFilled).get();
		orderInfo = waitForOrderComplete(orderInfo.getOrderId());

		System.out.println("Completed ETH sale");
		System.out.println(orderInfo);

		double dollarsAfterSale = listBalances();
		assertEquals(dollarsAfterSale, dollarsAfterPurchase + orderInfo.getExecutedValue() - orderInfo.getFillFees(),
			EPSILON);
		assertEquals(0.0030, orderInfo.getFillFees() / orderInfo.getExecutedValue(), EPSILON);
	}

	@Test
	void shouldPlacePostOnly()
		throws Exception
	{
		// get dollar balance
		System.out.println("Starting balances");
		double dollars = listBalances();

		// nothing to test if balance too low
		if(dollars < 10) {
			System.err.println("Not testing market orders - sandbox balance is too low");
			return;
		}

		// get current market on ETH
		var bbo = gdaxClient.getBBO(Product.ETH_USD).get();
		System.out
			.println("Market is " + bbo.bidPrice + "-" + bbo.askPrice + " (" + bbo.bidSize + " x " + bbo.askSize + ")");

		// post $10 of ETH specifying funds
		var price = Double.isNaN(bbo.bidPrice) ? 10.0 : bbo.bidPrice;
		var orderFuture =
			gdaxClient.placePostOnlyLimitOrder(Product.ETH_USD, OrderSide.BUY, 10.0 / price, price, null, TimeUnit.MINUTES);
		var orderInfo = orderFuture.get();
		UUID orderId = orderInfo.getOrderId();

		assertEquals(OrderType.LIMIT, orderInfo.getType());
		assertTrue(orderInfo.isPostOnly());
		assertEquals(TimeInForce.GTT, orderInfo.getTimeInForce());
		assertEquals(10.0 / price, orderInfo.getSize(), EPSILON);
		assertEquals(price, orderInfo.getPrice(), EPSILON);

		System.out.println("Initial order info");
		System.out.println(orderInfo);

		var response = gdaxClient.cancelOrder(orderId).get();
		System.err.println("Cancel response = " + response);

		try {
			gdaxClient.cancelOrder(UUID.randomUUID()).get();
			fail("Did not fail canceling invalid order");
		} catch(ExecutionException e2) {
			if(e2.getCause().getClass().isAssignableFrom(GdaxException.class)) {
				GdaxException e = (GdaxException) e2.getCause();
				assertEquals(404, e.getErrorCode());
				JsonObject jsonObject = new JsonParser().parse(e.getBody()).getAsJsonObject();
				assertEquals("order not found", jsonObject.get("message").getAsString());
			}
		}
	}
}