package com.mistrycapital.cryptobot.gdax.client;

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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GdaxClientTest {
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
		assertEquals(dollarsAfterPurchase, dollars - orderInfo.getExecutedValue() - orderInfo.getFillFees());
		assertEquals(0.0030, orderInfo.getFillFees() / orderInfo.getExecutedValue());

		// Now sell the ETH we just bought using amount (similar to actual execution)
		orderInfo =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.SELL, MarketOrderSizingType.SIZE, ethFilled).get();
		orderInfo = waitForOrderComplete(orderInfo.getOrderId());

		System.out.println("Completed ETH sale");
		System.out.println(orderInfo);

		double dollarsAfterSale = listBalances();
		assertEquals(dollarsAfterSale, dollarsAfterPurchase + orderInfo.getExecutedValue() - orderInfo.getFillFees());
		assertEquals(0.0030, orderInfo.getFillFees() / orderInfo.getExecutedValue());
	}
}