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
import com.mistrycapital.cryptobot.twilio.TwilioSender;
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
	void shouldPlaceMarketBuy() {
		final SimTimeKeeper timeKeeper = new SimTimeKeeper();
		timeKeeper.advanceTime(System.currentTimeMillis() * 1000000L);
		final OrderBookManager orderBookManager = mock(OrderBookManager.class);
		final OrderBook btcBook = mock(OrderBook.class);
		final Accountant accountant = mock(Accountant.class);
		final TwilioSender twilioSender = mock(TwilioSender.class);
		final GdaxClient gdaxClient = mock(GdaxClient.class);
		GdaxExecutionEngine executionEngine =
			new GdaxExecutionEngine(timeKeeper, accountant, orderBookManager, twilioSender, gdaxClient);

		when(orderBookManager.getBook(Product.BTC_USD)).thenReturn(btcBook);
		doAnswer(invocationOnMock -> {
			BBO bbo = (BBO) invocationOnMock.getArgument(0);
			bbo.askPrice = 10000.0;
			return null;
		}).when(btcBook).recordBBO(any());

		// first time order is pending
		JsonObject json = new JsonObject();
		json.addProperty("id", UUID.randomUUID().toString());
		json.addProperty("product_id", Product.BTC_USD.toString());
		json.addProperty("side", "buy");
		json.addProperty("type", "market");
		json.addProperty("created_at", timeKeeper.iso8601());
		json.addProperty("status", OrderStatus.PENDING.toString().toLowerCase());
		json.addProperty("settled", false);
		json.addProperty("funds", "10.0");
		json.addProperty("specified_funds", "10.0");
		OrderInfo orderInfo = new OrderInfo(json);
		when(gdaxClient.placeMarketOrder(Product.BTC_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 10.0))
			.thenReturn(CompletableFuture.supplyAsync(() -> orderInfo));

		// second time order is still pending
		// third time order is complete
		final double fillFees = 10.0 * 0.0030;
		final double executedValue = 10.0 - fillFees;
		final double filledSize = executedValue / 10000.0;
		json.addProperty("fill_fees", Double.toString(fillFees));
		json.addProperty("filled_size", Double.toString(filledSize));
		json.addProperty("executed_value", Double.toString(executedValue));
		json.addProperty("status", OrderStatus.DONE.toString().toLowerCase());
		json.addProperty("settled", true);
		json.addProperty("funds", "10.0");
		json.addProperty("specified_funds", "10.0");
		json.addProperty("done_at", timeKeeper.iso8601());
		json.addProperty("done_reason", Reason.FILLED.toString().toLowerCase());
		var orderInfo2 = new OrderInfo(json);
		assertEquals(fillFees, orderInfo2.getFillFees());
		assertEquals(filledSize, orderInfo2.getFilledSize());
		assertEquals(executedValue, orderInfo2.getExecutedValue());
		when(gdaxClient.getOrder(orderInfo.getOrderId()))
			.thenReturn(CompletableFuture.supplyAsync(() -> orderInfo))
			.thenReturn(CompletableFuture.supplyAsync(() -> orderInfo2));

		executionEngine.setOrderWaitForTesting();
		executionEngine.trade(Arrays.asList(new TradeInstruction(Product.BTC_USD, 10.0 / 10000.0, OrderSide.BUY)));

		verify(gdaxClient, times(1))
			.placeMarketOrder(Product.BTC_USD, OrderSide.BUY, MarketOrderSizingType.FUNDS, 10.0);

		ArgumentCaptor<Double> usdArg = ArgumentCaptor.forClass(Double.class);
		ArgumentCaptor<Double> cryptoArg = ArgumentCaptor.forClass(Double.class);
		verify(accountant, times(1))
			.recordTrade(same(Currency.USD), usdArg.capture(), same(Currency.BTC), cryptoArg.capture());
		assertEquals(-executedValue - fillFees, usdArg.getValue().doubleValue());
		assertEquals(filledSize, cryptoArg.getValue().doubleValue());
	}

	@Test
	void shouldPlaceMarketSell() {
		fail("not yet implemented");
	}
}