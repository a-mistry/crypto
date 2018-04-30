package com.mistrycapital.cryptobot.execution;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.MarketOrderSizingType;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.client.OrderStatus;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GdaxExecutionEngineTest {
	@Test
	void placeOrders()
		throws Exception
	{
		final SimTimeKeeper timeKeeper = new SimTimeKeeper();
		timeKeeper.advanceTime(System.currentTimeMillis()*1000000L);
		final OrderBookManager orderBookManager = mock(OrderBookManager.class);
		final OrderBook btcBook = mock(OrderBook.class);
		final Accountant accountant = mock(Accountant.class);
		final GdaxClient gdaxClient = mock(GdaxClient.class);
		GdaxExecutionEngine executionEngine =
			new GdaxExecutionEngine(timeKeeper, accountant, orderBookManager, gdaxClient);

		when(orderBookManager.getBook(Product.BTC_USD)).thenReturn(btcBook);
		doAnswer(invocationOnMock -> {
			BBO bbo = (BBO) invocationOnMock.getArgument(0);
			bbo.askPrice = 10000.0;
			return null;
		}).when(btcBook).recordBBO(any());

		final double fillFees = 10.0 * 0.0030;
		final double executedValue = 10.0 - fillFees;
		final double filledSize = executedValue / 10000.0;
		JsonObject json = new JsonObject();
		json.addProperty("id", UUID.randomUUID().toString());
		json.addProperty("product_id", Product.BTC_USD.toString());
		json.addProperty("side","buy");
		json.addProperty("type","market");
		json.addProperty("created_at", timeKeeper.iso8601());
		json.addProperty("fillFees", Double.toString(fillFees));
		json.addProperty("filledSize", Double.toString(filledSize));
		json.addProperty("executedValue", Double.toString(executedValue));
		json.addProperty("staus", OrderStatus.DONE.toString().toLowerCase());
		json.addProperty("settled", true);
		json.addProperty("funds", "10.0");
		json.addProperty("specifiedFunds", "10.0");
		json.addProperty("done_at", timeKeeper.iso8601());
		json.addProperty("done_reason", Reason.FILLED.toString().toLowerCase());
		when(gdaxClient.placeMarketOrder(Product.BTC_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 10.0))
			.thenReturn(CompletableFuture.supplyAsync(() -> new OrderInfo(json)));

		executionEngine.setOrderWaitForTesting();
		executionEngine.trade(Arrays.asList(new TradeInstruction(Product.BTC_USD, 10.0 / 10000.0, OrderSide.BUY)));
		Thread.sleep(1000);

		ArgumentCaptor<Double> usdArg = ArgumentCaptor.forClass(Double.class);
		ArgumentCaptor<Double> cryptoArg = ArgumentCaptor.forClass(Double.class);
		verify(accountant, times(1)).recordTrade(same(Currency.USD), usdArg.capture(), same(Currency.BTC), cryptoArg.capture());
		assertEquals(-executedValue-fillFees, usdArg.getValue().doubleValue());
		assertEquals(filledSize, cryptoArg.getValue().doubleValue());
	}
}