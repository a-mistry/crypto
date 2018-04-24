package com.mistrycapital.cryptobot.gdax;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.client.Account;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GdaxPositionsProviderTest {
	private static double EPSILON = 0.00000001;

	@Test
	void shouldGetPositions()
		throws Exception
	{
		GdaxClient gdaxClient = mock(GdaxClient.class);
		FakeTimeKeeper timeKeeper = new FakeTimeKeeper();
		timeKeeper.setTime(System.currentTimeMillis());
		GdaxPositionsProvider positionsProvider = new GdaxPositionsProvider(timeKeeper, gdaxClient);

		List<Account> accounts = new ArrayList<>();

		JsonObject json = new JsonObject();
		json.addProperty("id", "71452118-efc7-4cc4-8780-a5e22d4baa53");
		json.addProperty("currency", "BTC");
		json.addProperty("balance", "2.0000000000000000");
		json.addProperty("available", "1.0000000000000000");
		json.addProperty("hold", "0.0000000000000000");
		json.addProperty("profile_id", "75da88c5-05bf-4f54-bc85-5c775bd68254");
		accounts.add(new Account(json));
		json.addProperty("id", "e316cb9a-0808-4fd7-8914-97829c1925de");
		json.addProperty("currency", "USD");
		json.addProperty("balance", "80.2301373066930000");
		json.addProperty("available", "79.2266348066930000");
		json.addProperty("hold", "1.0035025000000000");
		json.addProperty("profile_id", "75da88c5-05bf-4f54-bc85-5c775bd68254");
		accounts.add(new Account(json));

		when(gdaxClient.getAccounts()).thenReturn(CompletableFuture.supplyAsync(() -> accounts));
		double[] available = positionsProvider.getAvailable();
		double[] balance = positionsProvider.getBalance();

		verify(gdaxClient, times(1)).getAccounts();
		assertEquals(Currency.count, available.length);
		for(Currency currency : Currency.FAST_VALUES) {
			int index = currency.getIndex();
			switch(currency) {
				case BTC:
					assertEquals(1.0, available[index], EPSILON);
					assertEquals(2.0, balance[index], EPSILON);
					break;
				case USD:
					assertEquals(79.2266348066930000, available[index], EPSILON);
					assertEquals(80.2301373066930000, balance[index], EPSILON);
					break;
				default:
					assertEquals(0.0, available[index], EPSILON);
					assertEquals(0.0, balance[index], EPSILON);
			}
		}
	}
}