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

	@Test
	void shouldPlaceMarketOrder()
		throws Exception
	{
		// get dollar balance
		List<Account> accountList = gdaxClient.getAccounts().get();
		double dollars = accountList.stream()
			.filter(account -> account.getCurrency() == Currency.USD)
			.findAny()
			.get()
			.getAvailable();

		// nothing to test if balance too low
		if(dollars < 100) {
			System.err.println("Not testing market orders - sandbox balance is too low");
			return;
		}
		System.err.println("Dollars = " + dollars);

		var orderFuture =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 100);
		var orderInfo = orderFuture.get();
		UUID orderId = orderInfo.getOrderId();

		assertEquals(Product.ETH_USD, orderInfo.getProduct());
		assertEquals(OrderSide.BUY, orderInfo.getOrderSide());
		assertEquals(OrderType.MARKET, orderInfo.getType());
		assertEquals(TimeInForce.GTC, orderInfo.getTimeInForce());
		assertEquals(SelfTradePreventionFlag.DC, orderInfo.getSelfTradePreventionFlag());
		assertFalse(orderInfo.isPostOnly());

		System.out.println(orderInfo);

		// now repeatedly check order until executed
		for(int i = 0; i < 10; i++) {
			orderInfo = gdaxClient.getOrder(orderId).get();
			if(orderInfo.getStatus() == OrderStatus.DONE)
				break;
			Thread.sleep(1000);
		}

		System.out.println(orderInfo);
		double ethFilled = orderInfo.getFilledSize();

		gdaxClient.getAccounts().get().stream()
			.forEach(account -> System.err.println(account.getCurrency() + " " + account.getAvailable()));

		orderInfo =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.SELL, MarketOrderSizingType.SIZE, ethFilled).get();
		orderId = orderInfo.getOrderId();

		// now repeatedly check order until executed
		for(int i = 0; i < 10; i++) {
			orderInfo = gdaxClient.getOrder(orderId).get();
			if(orderInfo.getStatus() == OrderStatus.DONE)
				break;
			Thread.sleep(1000);
		}

		System.out.println(orderInfo);

		gdaxClient.getAccounts().get().stream()
			.forEach(account -> System.err.println(account.getCurrency() + " " + account.getAvailable()));
	}
}