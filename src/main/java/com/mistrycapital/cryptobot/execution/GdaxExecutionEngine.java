package com.mistrycapital.cryptobot.execution;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient.GdaxException;
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static com.mistrycapital.cryptobot.gdax.client.GdaxClient.gdaxDecimalFormat;

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
	private double filledAmountTotal;
	private double orderedAmountTotal;

	/**
	 * Execution strategy that posts at the current bid/ask and cancels any outstanding amount if not completed within
	 * 80% of the current interval time
	 */
	private void post(TradeInstruction instruction) {
		log.debug("Posting instruction " + instruction);
		Product product = instruction.getProduct();
		BBO bbo = orderBookManager.getBook(product).getBBO();
		final double price = instruction.getOrderSide() == OrderSide.BUY ? bbo.bidPrice : bbo.askPrice;
		if(Double.isNaN(price)) {
			log.error("Tried to post order to " + instruction + " but top of book is NaN so we can't determine price");
			return;
		}

		UUID clientOid = UUID.randomUUID();
		synchronized(gdaxIds) {
			clientOids.put(clientOid, instruction);
		}

		final CompletableFuture<OrderInfo> orderInfoFuture;
		orderInfoFuture = gdaxClient.placePostOnlyLimitOrder(product, instruction.getOrderSide(),
			instruction.getAmount(), price, clientOid, TimeUnit.HOURS);
		log.info("Placed post-only order to " + instruction + " at " + price + " with 1 hour GTT, client_oid "
			+ clientOid);

		dbRecorder.recordPostOnlyAttempt(instruction, clientOid, price);

		// check for client errors
		try {
			orderInfoFuture.get();
		} catch(ExecutionException | InterruptedException e) {
			if(e.getCause().getClass().isAssignableFrom(GdaxException.class)) {
				log.error("Could not post order to " + instruction, e);
				clientOids.remove(clientOid);
			} else {
				throw new RuntimeException(e);
			}
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
				TradeInstruction instruction = clientOids.get(clientOid);
				log.debug("Got RECEIVED message for post order to " + instruction + " client_oid " + clientOid
					+ " maps to gdax order_id: " + msg.getOrderId());
				gdaxIds.put(msg.getOrderId(), instruction);
				clientOids.remove(clientOid);
				dbRecorder.updatePostOnlyId(clientOid, msg.getOrderId());
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
				UUID orderId = msg.getOrderId();
				TradeInstruction instruction = gdaxIds.get(orderId);
				gdaxIds.remove(orderId);

				final double originalSize = instruction.getAmount();
				final double remainingSize = msg.getRemainingSize();
				final double filledAmount = originalSize - remainingSize;
				final String originalSizeStr = gdaxDecimalFormat.format(originalSize);
				final String remainingSizeStr = gdaxDecimalFormat.format(remainingSize);
				final String filledAmountStr = gdaxDecimalFormat.format(filledAmount);
				final String timeStr = ZonedDateTime.ofInstant(
					Instant.ofEpochMilli(msg.getTimeMicros()),
					ZoneOffset.UTC
				).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

				log.info("Post order " + orderId + " was done at " + timeStr
					+ " with reason " + msg.getReason() + " and remaining size " + remainingSizeStr
					+ ", original size was " + originalSizeStr);

				filledAmountTotal += filledAmount;
				orderedAmountTotal += instruction.getAmount();
				log.info("Fill rate is running at " + (100 * filledAmountTotal / orderedAmountTotal));

				// Record in db and send text message
				if(msg.getRemainingSize() > 0)
					dbRecorder.recordPostOnlyCancel(msg, originalSize);

				final String text = (msg.getOrderSide() == OrderSide.BUY ? "Bot " : "Sold ")
					+ filledAmountStr + " " + msg.getProduct() + " at " + msg.getPrice()
					+ " with " + remainingSizeStr + " remaining";
				twilioSender.sendMessage(text);
			}
		}
	}

	@Override
	public void process(final Match msg) {
		synchronized(gdaxIds) {
			if(gdaxIds.containsKey(msg.getMakerOrderId())) {
				UUID orderId = msg.getMakerOrderId();
				log.info("FILL received for post order " + orderId + ": " + gdaxDecimalFormat.format(msg.getSize())
					+ " " + msg.getOrderSide() + " " + msg.getProduct() + " at " + msg.getPrice());
				tactic.notifyFill(gdaxIds.get(msg.getMakerOrderId()), msg.getProduct(), msg.getOrderSide(),
					msg.getSize(), msg.getPrice());

				final int buySign = msg.getOrderSide() == OrderSide.BUY ? 1 : -1;
				accountant.recordTrade(Currency.USD, -buySign * msg.getSize() * msg.getPrice(),
					msg.getProduct().getCryptoCurrency(), buySign * msg.getSize());

				dbRecorder.recordPostOnlyFill(msg);
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
