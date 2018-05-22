package com.mistrycapital.cryptobot.execution;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.MarketOrderSizingType;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.client.OrderStatus;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.twilio.TwilioSender;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GdaxExecutionEngine implements ExecutionEngine, GdaxMessageProcessor {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final OrderBookManager orderBookManager;
	private final DBRecorder dbRecorder;
	private final TwilioSender twilioSender;
	private final Tactic tactic;
	private final GdaxClient gdaxClient;
	/** Executor used to check market order status */
	private final ScheduledExecutorService executorService;
	/** Check after this many seconds */
	private int waitForOrderSeconds = 15;

	public GdaxExecutionEngine(TimeKeeper timeKeeper, Accountant accountant, OrderBookManager orderBookManager,
		DBRecorder dbRecorder, TwilioSender twilioSender, Tactic tactic, GdaxClient gdaxClient)
	{
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
		this.dbRecorder = dbRecorder;
		this.twilioSender = twilioSender;
		this.tactic = tactic;
		this.gdaxClient = gdaxClient;
		executorService = Executors.newScheduledThreadPool(1);
	}

	public void trade(List<TradeInstruction> instructions) {
		if(instructions == null) return;

		for(TradeInstruction instruction : instructions) {
			switch(instruction.getAggression()) {
				case TAKE:
					take(instruction);
					break;
				case POST_ONLY:
					post(instruction);
					break;
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////
	// METHODS FOR TAKING STRATEGIES

	/** Execution strategy that uses market orders to take the amount at the current price */
	private void take(TradeInstruction instruction) {
		Product product = instruction.getProduct();
		if(instruction.getOrderSide() == OrderSide.BUY) {
			// for buy orders, use funds so we know we have enough after transaction costs
			BBO bbo = orderBookManager.getBook(product).getBBO();
			double dollars = bbo.askPrice * instruction.getAmount();
			gdaxClient.placeMarketOrder(product, OrderSide.BUY, MarketOrderSizingType.FUNDS, dollars)
				.thenAccept(this::verifyOrderComplete);
			log.info("Placed order to buy $" + dollars + " of " + product);
		} else {
			gdaxClient
				.placeMarketOrder(product, OrderSide.SELL, MarketOrderSizingType.SIZE, instruction.getAmount())
				.thenAccept(this::verifyOrderComplete);
			log.info("Placed order to sell " + instruction.getAmount() + " of " + product);
		}
	}

	/**
	 * Used for testing - sets the interval that execution waits before checking if an order is complete to zero
	 */
	void setOrderWaitForTesting() {
		waitForOrderSeconds = 0;
	}

	private synchronized void verifyOrderComplete(OrderInfo orderInfo) {
		// only try getting status for a minute, then log error and continue
		if(orderInfo.getStatus() != OrderStatus.DONE
			&& timeKeeper.epochMs() - orderInfo.getTimeMicros() / 1000L > 60000L) {

			log.error("Order was not done after 1 minute: " + orderInfo);
			accountant.refreshPositions(); // make sure we have accurate positions
			twilioSender.sendMessage(
				"Order not complete after 1 min " + orderInfo.getOrderSide() + " " + orderInfo.getProduct()
					+ " funds " + orderInfo.getFunds() + " size " + orderInfo.getSize());
			dbRecorder.recordTrade(orderInfo);
			return;
		}

		if(orderInfo.getStatus() != OrderStatus.DONE) {
			// wait before trying again to avoid rate limit problems
			Runnable getOrderInfo = () -> gdaxClient.getOrder(orderInfo.getOrderId())
				.thenAccept(this::verifyOrderComplete);
			executorService.schedule(getOrderInfo, waitForOrderSeconds, TimeUnit.SECONDS);
		} else if(orderInfo.getDoneReason() == Reason.CANCELED) {
			log.error("Order was canceled: " + orderInfo);
			twilioSender.sendMessage(
				"Order canceled " + orderInfo.getOrderSide() + " " + orderInfo.getProduct() + " funds " +
					orderInfo.getFunds() + " size " + orderInfo.getSize());
			dbRecorder.recordTrade(orderInfo);
		} else if(orderInfo.getDoneReason() == Reason.FILLED) {
			if(orderInfo.getOrderSide() == OrderSide.BUY) {
				final double dollarsSpent = orderInfo.getExecutedValue() + orderInfo.getFillFees();
				final double cryptoPurchased = orderInfo.getFilledSize();
				accountant.recordTrade(Currency.USD, -dollarsSpent, orderInfo.getProduct().getCryptoCurrency(),
					cryptoPurchased);
				dbRecorder.recordTrade(orderInfo);
				twilioSender.sendMessage(
					"Bot " + cryptoPurchased + " " + orderInfo.getProduct().getCryptoCurrency() + " @ $" +
						orderInfo.getExecutedValue() / orderInfo.getFilledSize());
			} else {
				final double dollarsReceived = orderInfo.getExecutedValue() - orderInfo.getFillFees();
				final double cryptoSold = orderInfo.getFilledSize();
				accountant.recordTrade(Currency.USD, dollarsReceived, orderInfo.getProduct().getCryptoCurrency(),
					-cryptoSold);
				dbRecorder.recordTrade(orderInfo);
				twilioSender.sendMessage(
					"Sold " + cryptoSold + " " + orderInfo.getProduct().getCryptoCurrency() + " @ $" +
						orderInfo.getExecutedValue() / orderInfo.getFilledSize());
			}
		} else {
			log.error("Order was neither canceled nor filled: " + orderInfo);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////
	// METHODS FOR POSTING STRATEGIES

	private Map<UUID,TradeInstruction> clientOids = new HashMap<>();
	private Map<UUID,TradeInstruction> gdaxIds = new HashMap<>();

	/**
	 * Execution strategy that posts at the current bid/ask and cancels any outstanding amount if not completed within
	 * 80% of the current interval time
	 */
	private void post(TradeInstruction instruction) {
		Product product = instruction.getProduct();
		BBO bbo = orderBookManager.getBook(product).getBBO();
		UUID clientOid = UUID.randomUUID();
		if(instruction.getOrderSide() == OrderSide.BUY) {
			gdaxClient.placePostOnlyLimitOrder(product, OrderSide.BUY, instruction.getAmount(), bbo.bidPrice,
				clientOid, TimeUnit.HOURS);
			log.info("Placed post-only order to buy " + instruction.getAmount() + " of " + product + " at bid " +
				bbo.bidPrice + " with 1 hour GTT");
		} else {
			gdaxClient.placePostOnlyLimitOrder(product, OrderSide.SELL, instruction.getAmount(), bbo.askPrice,
				clientOid, TimeUnit.HOURS);
			log.info("Placed post-only order to sell " + instruction.getAmount() + " of " + product + " at ask " +
				bbo.askPrice + " with 1 hour GTT");
		}
	}

	@Override
	public void process(final Book msg) {
	}

	@Override
	public void process(final Received msg) {
		synchronized(gdaxIds) {
			UUID clientOid = msg.getClientOid();
			if(clientOid != null && clientOids.containsKey(clientOid)) {
				gdaxIds.put(msg.getOrderId(), clientOids.get(clientOid));
				clientOids.remove(clientOid);
			}
		}
	}

	@Override
	public void process(final Open msg) {
	}

	@Override
	public void process(final Done msg) {
		synchronized(gdaxIds) {
			if(gdaxIds.containsKey(msg.getOrderId())) {
				gdaxIds.remove(msg.getOrderId());
			}
		}
	}

	@Override
	public void process(final Match msg) {
		synchronized(gdaxIds) {
			if(gdaxIds.containsKey(msg.getMakerOrderId())) {
				tactic.notifyFill(gdaxIds.get(msg.getMakerOrderId()), msg.getProduct(), msg.getOrderSide(),
					msg.getSize(), msg.getPrice());
			}
		}
	}

	@Override
	public void process(final ChangeSize msg) {
	}

	@Override
	public void process(final ChangeFunds msg) {
	}

	@Override
	public void process(final Activate msg) {
	}
}
