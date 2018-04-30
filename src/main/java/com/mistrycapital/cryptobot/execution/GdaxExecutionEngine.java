package com.mistrycapital.cryptobot.execution;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.MarketOrderSizingType;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.client.OrderStatus;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GdaxExecutionEngine implements ExecutionEngine {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final OrderBookManager orderBookManager;
	private final GdaxClient gdaxClient;
	private final ScheduledExecutorService executorService;

	public GdaxExecutionEngine(TimeKeeper timeKeeper, Accountant accountant, OrderBookManager orderBookManager,
		GdaxClient gdaxClient)
	{
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
		this.gdaxClient = gdaxClient;
		executorService = Executors.newScheduledThreadPool(1);
	}

	public void trade(List<TradeInstruction> instructions) {
		if(instructions == null) return;

		BBO bbo = new BBO();

		for(TradeInstruction instruction : instructions) {
			Product product = instruction.getProduct();
			if(instruction.getOrderSide() == OrderSide.BUY) {
				// for buy orders, use funds so we know we have enough after transaction costs
				orderBookManager.getBook(product).recordBBO(bbo);
				double dollars = bbo.askPrice * instruction.getAmount();
				gdaxClient.placeMarketOrder(product, OrderSide.BUY, MarketOrderSizingType.FUNDS, dollars)
					.thenAccept(this::verifyOrderComplete);
			} else {
				gdaxClient
					.placeMarketOrder(product, OrderSide.SELL, MarketOrderSizingType.SIZE, instruction.getAmount())
					.thenAccept(this::verifyOrderComplete);
			}
		}
	}

	public synchronized void verifyOrderComplete(OrderInfo orderInfo) {
		// only try getting status for a minute, then log error and continue
		if(orderInfo.getStatus() != OrderStatus.DONE
			&& timeKeeper.epochMs() - orderInfo.getTimeMicros() / 1000L > 60000L) {

			log.error("Order was not done after 1 minute: " + orderInfo);
			accountant.refreshPositions(); // make sure we have accurate positions
			return;
		}

		if(orderInfo.getStatus() != OrderStatus.DONE) {
			// wait 15 seconds before trying again to avoid rate limit problems
			Runnable getOrderInfo = () -> gdaxClient.getOrder(orderInfo.getOrderId())
				.thenAccept(this::verifyOrderComplete);
			executorService.schedule(getOrderInfo, 15, TimeUnit.SECONDS);
		} else if(orderInfo.getDoneReason() == Reason.CANCELED) {
			log.error("Order was canceled: " + orderInfo);
		} else if(orderInfo.getDoneReason() == Reason.FILLED) {
			if(orderInfo.getOrderSide() == OrderSide.BUY) {
				final double dollarsSpent = orderInfo.getExecutedValue() + orderInfo.getFillFees();
				final double cryptoPurchased = orderInfo.getFilledSize();
				accountant.recordTrade(Currency.USD, -dollarsSpent, orderInfo.getProduct().getCryptoCurrency(),
					cryptoPurchased);
			} else {
				final double dollarsReceived = orderInfo.getExecutedValue() - orderInfo.getFillFees();
				final double cryptoSold = orderInfo.getFilledSize();
				accountant.recordTrade(Currency.USD, dollarsReceived, orderInfo.getProduct().getCryptoCurrency(),
					-cryptoSold);
			}
		} else {
			log.error("Order was neither canceled nor filled: " + orderInfo);
		}
	}
}
