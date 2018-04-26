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
		if(dollars < 10) {
			System.err.println("Not testing market orders - sandbox balance is too low");
			return;
		}

		var orderFuture =
			gdaxClient.placeMarketOrder(Product.ETH_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 10.0);
		var orderInfo = orderFuture.get();
		UUID orderId = orderInfo.getOrderId();

		assertEquals(Product.ETH_USD, orderInfo.getProduct());
		assertEquals(OrderSide.BUY, orderInfo.getOrderSide());
		assertEquals(OrderType.MARKET, orderInfo.getType());
		assertEquals(TimeInForce.GTC, orderInfo.getTimeInForce());
		assertFalse(orderInfo.isPostOnly());

		// now repeatedly check order until executed

		System.out.println(orderInfo);
		fail("not yet implemented");
	}
}