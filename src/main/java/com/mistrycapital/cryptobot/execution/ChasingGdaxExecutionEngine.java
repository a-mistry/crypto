package com.mistrycapital.cryptobot.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.book.TopOfBookSubscriber;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.client.OrderStatus;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.twilio.TwilioSender;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static com.mistrycapital.cryptobot.execution.ChasingGdaxExecutionEngine.WorkingOrderStatus.POSTED;
import static com.mistrycapital.cryptobot.execution.ChasingGdaxExecutionEngine.WorkingOrderStatus.POSTING;
import static com.mistrycapital.cryptobot.gdax.client.GdaxClient.gdaxDecimalFormat;

/**
 * Execution algo that posts at the current BBO, then cancels/reposts on each tick
 */
public class ChasingGdaxExecutionEngine implements ExecutionEngine, GdaxMessageProcessor, TopOfBookSubscriber {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final long ONE_HOUR_NANOS = 60 * 60 * 1000000000L;
	private static final double TICK_SIZE = 0.01;
	private static final int MAX_TRIES = 10;

	private final TimeKeeper timeKeeper;
	private final OrderBookManager orderBookManager;
	private final Accountant accountant;
	private final Tactic tactic;
	private final DBRecorder dbRecorder;
	private final TwilioSender twilioSender;
	private final GdaxClient gdaxClient;
	private final ExecutorService executorService;
	private final Object lockObj;
	/** All working orders by client oid */
	private final Map<UUID,WorkingOrder> workingOrdersByClientOid;
	/** All working orders */
	private final List<WorkingOrder> workingOrders;
	/** Working orders that have been received by gdax and have an order id */
	private final Map<UUID,WorkingOrder> workingOrdersByOrderId;
	/** Bid for each product */
	private final double[] bids;
	/** Ask for each product */
	private final double[] asks;

	enum WorkingOrderStatus {
		POSTING,
		POSTED
	}

	class WorkingOrder {
		final long origTimeNanos;
		final TradeInstruction instruction;
		final UUID clientOid;
		final double origPrice;
		double postedPrice;
		int attemptCount;
		UUID orderId;
		double filledAmountxPrice;
		double filledAmount;
		WorkingOrderStatus status;
		double bidThreshold;

		WorkingOrder(final TradeInstruction instruction, final UUID clientOid, final double postedPrice) {
			this.instruction = instruction;
			this.clientOid = clientOid;
			this.postedPrice = postedPrice;
			origTimeNanos = timeKeeper.epochNanos();
			origPrice = postedPrice;
			status = POSTING;
			bidThreshold = instruction.getOrderSide() == OrderSide.BUY
				? origPrice * (1 + 0.5 * instruction.getForecast()) : Double.NaN;
		}
	}

	public ChasingGdaxExecutionEngine(TimeKeeper timeKeeper, Accountant accountant, OrderBookManager orderBookManager,
		DBRecorder dbRecorder, TwilioSender twilioSender, Tactic tactic, GdaxClient gdaxClient)
	{
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		this.accountant = accountant;
		this.tactic = tactic;
		this.dbRecorder = dbRecorder;
		this.twilioSender = twilioSender;
		this.gdaxClient = gdaxClient;
		executorService = Executors.newFixedThreadPool(2);
		lockObj = new Object();
		workingOrders = new ArrayList<>();
		workingOrdersByClientOid = new HashMap<>();
		workingOrdersByOrderId = new HashMap<>();
		bids = new double[Product.count];
		asks = new double[Product.count];
		for(int i = 0; i < bids.length; i++)
			bids[i] = asks[i] = Double.NaN;

		// cancel any outstanding orders on reset
		gdaxClient.cancelAll()
			.thenAccept(json -> {
				JsonArray jsonArray = json.getAsJsonArray();
				for(JsonElement element : jsonArray) {
					log.info("On execution engine restart canceled order " + element.getAsString());
				}
			});
	}

	@Override
	public void trade(final List<TradeInstruction> instructions) {
		if(instructions == null) return;

		for(TradeInstruction instruction : instructions) {
			post(instruction);
		}
	}

	/** Run task in the background - used to run DB queries / twilio calls outside of this thread */
	private void runInBackground(Runnable runnable) {
		executorService.submit(runnable);
	}

	private void post(TradeInstruction instruction) {
		log.debug("Posting instruction " + instruction);
		final Product product = instruction.getProduct();
		final UUID clientOid = UUID.randomUUID();
		final int productIndex = product.getIndex();
		if(Double.isNaN(bids[productIndex])) {
			// this happens the first time - check BBO
			final BBO bbo = orderBookManager.getBook(product).getBBO();
			bids[productIndex] = bbo.bidPrice;
			asks[productIndex] = bbo.askPrice;
		}
		final double postedPrice = calcPostPrice(instruction.getOrderSide(), bids[productIndex], asks[productIndex]);
		if(Double.isNaN(postedPrice)) {
			log.error("Tried to post order to " + instruction + " but top of book is NaN so we can't determine price");
			tactic.notifyReject(instruction);
			return;
		}
		final WorkingOrder workingOrder = new WorkingOrder(instruction, clientOid, postedPrice);
		synchronized(lockObj) {
			workingOrders.add(workingOrder);
			workingOrdersByClientOid.put(clientOid, workingOrder);
		}

		runInBackground(() -> {
			dbRecorder.recordPostOnlyAttempt(instruction, clientOid, postedPrice);
		});

		CompletableFuture<OrderInfo> orderFuture = postWorkingOrder(workingOrder);
		processSentWorkingOrder(workingOrder, orderFuture);
	}

	private double calcPostPrice(final OrderSide side, final double bidPrice, final double askPrice) {
		if(askPrice - bidPrice < TICK_SIZE + OrderBook.PRICE_EPSILON) {
			return side == OrderSide.BUY ? bidPrice : askPrice;
		} else {
			// spread is more than one tick. jump the line in this case to get executed faster
			return side == OrderSide.BUY ? bidPrice + TICK_SIZE : askPrice - TICK_SIZE;
		}
	}

	/**
	 * Posts the given working order. Assumes the price has been set correctly and that the order has not
	 * already been posted
	 */
	private CompletableFuture<OrderInfo> postWorkingOrder(final WorkingOrder workingOrder) {
		final TradeInstruction instruction = workingOrder.instruction;

		log.info("Placing post-only order to " + instruction + " at " + workingOrder.postedPrice
			+ " with 1 hour GTT, client_oid " + workingOrder.clientOid);

		return gdaxClient.placePostOnlyLimitOrder(instruction.getProduct(), instruction.getOrderSide(),
			instruction.getAmount(), workingOrder.postedPrice, workingOrder.clientOid, TimeUnit.HOURS);
	}

	/**
	 * Handles retry logic and exceptions arising from the api call to post an order. This is kept separate
	 * from placing the order so it can be done in a separate thread
	 */
	private void processSentWorkingOrder(final WorkingOrder workingOrder,
		final CompletableFuture<OrderInfo> orderFuture)
	{
		boolean removeOrder = false;
		final TradeInstruction instruction = workingOrder.instruction;
		try {
			final OrderInfo orderInfo = orderFuture.get();
			if(orderInfo.getStatus() == OrderStatus.REJECTED) {
				if("post only".equals(orderInfo.getRejectReason()) && workingOrder.attemptCount < MAX_TRIES) {

					// Retry with new price up to MAX_TRIES times

					final int productIndex = instruction.getProduct().getIndex();
					final double bidPrice = bids[productIndex];
					final double askPrice = asks[productIndex];

					log.error("Order to " + instruction + " was rejected as post-only. " +
						"Retrying at current top of book" + bids[productIndex] + "-" + asks[productIndex] + "...");

					workingOrder.attemptCount++;
					workingOrder.postedPrice =
						calcPostPrice(workingOrder.instruction.getOrderSide(), bidPrice, askPrice);

					final CompletableFuture<OrderInfo> newFuture = postWorkingOrder(workingOrder);
					runInBackground(() -> processSentWorkingOrder(workingOrder, newFuture));

				} else if("post only".equals(orderInfo.getRejectReason())) {

					log.error(
						"Order to " + instruction +
							" was rejected and hit the maximum attempt count, not retrying");
					removeOrder = true;

				} else {

					log.error("Order to " + instruction + " was rejected due to " + orderInfo.getRejectReason()
						+ ", not retrying");
					removeOrder = true;

				}
			}

		} catch(ExecutionException | InterruptedException e) {
			Class<?> clazz = e.getCause().getClass();
			if(clazz.isAssignableFrom(GdaxClient.GdaxException.class) || clazz.isAssignableFrom(IOException.class)) {
				log.error("Could not post order to " + workingOrder.instruction, e.getCause());
				removeOrder = true;
			} else {
				throw new RuntimeException(e);
			}
		}

		if(removeOrder) {
			synchronized(lockObj) {
				workingOrders.remove(workingOrder);
				workingOrdersByClientOid.remove(workingOrder.clientOid);
			}
			tactic.notifyReject(instruction);
		}
	}

	private void cancelRepost(final WorkingOrder workingOrder, final UUID oldOrderId) {
		// Simultaneously cancel and repost if we have the funds to do so; otherwise, cancel first
		final TradeInstruction instruction = workingOrder.instruction;
		final var amount = instruction.getAmount() - workingOrder.filledAmount;
		final var isBuy = instruction.getOrderSide() == OrderSide.BUY;
		final var available = isBuy ? accountant.getAvailable(Currency.USD)
			: accountant.getAvailable(instruction.getProduct().getCryptoCurrency());
		final var needed = isBuy ? amount * workingOrder.postedPrice : amount;
		final boolean simultaneous = available >= needed;

		final CompletableFuture<OrderInfo> orderFuture;
		final CompletableFuture<?> cancelFuture;
		if(simultaneous) {
			log.info("Simultaneous cancel/reposting " + workingOrder.instruction + " at " + workingOrder.postedPrice
				+ " with 1 hour GTT, client_oid " + workingOrder.clientOid);

			orderFuture = postWorkingOrder(workingOrder);
			cancelFuture = gdaxClient.cancelOrder(oldOrderId);
		} else {
			cancelFuture = null;
			orderFuture = gdaxClient.cancelOrder(oldOrderId)
				.thenCompose(json -> {
					// refresh prices here, just before we send the order
					final int productIndex = workingOrder.instruction.getProduct().getIndex();
					final double newPostedPrice = calcPostPrice(workingOrder.instruction.getOrderSide(),
						bids[productIndex], asks[productIndex]);
					workingOrder.postedPrice = newPostedPrice;

					log.info("Canceled, now reposting order to " + workingOrder.instruction);

					return postWorkingOrder(workingOrder);
				});
		}

		runInBackground(() -> {
			processSentWorkingOrder(workingOrder, orderFuture);

			if(cancelFuture != null)
				try {
					cancelFuture.get();
				} catch(ExecutionException | InterruptedException e) {
					if(e.getCause().getClass().isAssignableFrom(GdaxClient.GdaxException.class)) {
						log.error("Error canceling order to " + workingOrder.instruction, e);
					} else {
						throw new RuntimeException(e);
					}
				}
		});
	}

	@Override
	public void onChanged(final Product product, final OrderSide side, final double bidPrice, final double askPrice) {
		// update BBO and look for widening
		final int productIndex = product.getIndex();
		final double prevBid = bids[productIndex];
		final double prevAsk = asks[productIndex];
		bids[productIndex] = bidPrice;
		asks[productIndex] = askPrice;
		final double prevSpread = prevAsk - prevBid;
		final double spread = askPrice - bidPrice;
		final boolean spreadWidened = spread > prevSpread + OrderBook.PRICE_EPSILON && spread >= 2 * TICK_SIZE;

		List<WorkingOrder> ordersToRepost = null;
		List<WorkingOrder> markCanceled = null;
		synchronized(lockObj) {
			// check if any relevant working orders for product/side
			// We need to take action under two conditions
			// 1. The side we're on has repriced
			// 2. The other side has repriced farther and now the spread is >= 2 ticks. In this case we can
			// try to jump to the front of the line
			boolean noWorkingOrders = true;
			for(WorkingOrder workingOrder : workingOrders)
				if(workingOrder.instruction.getProduct() == product &&
					(workingOrder.instruction.getOrderSide() == side || spreadWidened))
				{
					noWorkingOrders = false;
					break;
				}
			if(noWorkingOrders) return;

			log.debug("Price ticked away while we have working orders, verifying " + product + " side " + side
				+ " for new top " + bidPrice + "-" + askPrice);

			// search working orders for any that need to be repriced or removed
			final long timeNanos = timeKeeper.epochNanos();
			for(WorkingOrder workingOrder : workingOrders) {
				// remove any orders that should have been canceled but for some reason were not marked as such
				if(timeNanos - workingOrder.origTimeNanos > ONE_HOUR_NANOS) {
					if(markCanceled == null) markCanceled = new LinkedList<>();
					markCanceled.add(workingOrder);
					continue;
				}

				// if order is for a different product or has not yet been posted, we don't care
				if(workingOrder.instruction.getProduct() != product || workingOrder.status != POSTED)
					continue;

				final OrderSide workingOrderSide = workingOrder.instruction.getOrderSide();
				final boolean isBuy = workingOrderSide == OrderSide.BUY;

				final boolean shouldRepost;
				if(workingOrderSide == side) {
					// Case 1 - reprice to new top of book if needed
					final boolean bidMovedAway = isBuy
						&& workingOrder.postedPrice < bidPrice - OrderBook.PRICE_EPSILON;
					final boolean askMovedAway = workingOrderSide == OrderSide.SELL
						&& workingOrder.postedPrice > askPrice + OrderBook.PRICE_EPSILON;

					// Optimization - don't buy if bid moved too far away (1/2 forecast value)
					if(bidMovedAway && bidPrice < workingOrder.bidThreshold - OrderBook.PRICE_EPSILON) {
						shouldRepost = false;
						if(markCanceled == null) markCanceled = new LinkedList<>();
						markCanceled.add(workingOrder);
						tactic.notifyReject(workingOrder.instruction);
					} else {
						shouldRepost = askMovedAway || bidMovedAway;
					}
				} else {
					// Case 2 - reprice if spread has widened and so we can be aggressive
					shouldRepost = spreadWidened;
				}
				if(shouldRepost) {
					if(ordersToRepost == null) ordersToRepost = new LinkedList<>();
					ordersToRepost.add(workingOrder);
				}
			}

			// remove any orders that should have been canceled but we did not have a record
			if(markCanceled != null)
				for(WorkingOrder workingOrder : markCanceled) {
					workingOrders.remove(workingOrder);
					workingOrdersByClientOid.remove(workingOrder.clientOid);
					if(workingOrder.orderId != null) workingOrdersByOrderId.remove(workingOrder.orderId);
					workingOrder = null; // mark for GC
				}

			// reset maps and working order status inside synchronized block
			if(ordersToRepost != null)
				for(WorkingOrder workingOrder : ordersToRepost) {
					final double newPrice = calcPostPrice(workingOrder.instruction.getOrderSide(), bidPrice, askPrice);
					log.info("Cancel/reposting order to " + workingOrder.instruction + " old price "
						+ workingOrder.postedPrice + " new price " + newPrice + " with 1 hour GTT, client_oid " +
						workingOrder.clientOid + " gdax order id " + workingOrder.orderId);

					workingOrdersByOrderId.remove(workingOrder.orderId);
					workingOrder.status = POSTING;
					workingOrder.postedPrice = newPrice;
				}
		}

		// cancel/repost any relevant working orders
		if(ordersToRepost != null)
			for(WorkingOrder workingOrder : ordersToRepost) {
				UUID oldOrderId = workingOrder.orderId;
				workingOrder.orderId = null;
				cancelRepost(workingOrder, oldOrderId);
			}
	}

	@Override
	public void process(final Book msg) {
		// nothing to do
	}

	@Override
	public void process(final Received msg) {
		final UUID clientOid = msg.getClientOid();
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			workingOrder = workingOrdersByClientOid.get(clientOid);
			if(workingOrder != null) {
				workingOrdersByOrderId.put(msg.getOrderId(), workingOrder);
			}
		}
		if(workingOrder != null) {
			workingOrder.orderId = msg.getOrderId();
			workingOrder.status = POSTED;
			log.debug("Got RECEIVED message for post order to " + workingOrder.instruction + " client_oid " + clientOid
				+ " maps to gdax order_id: " + workingOrder.orderId);
			// No need to save in database here because we keep track of the client oid through fills

			runInBackground(accountant::refreshPositions); // note the available balance change
		}
	}

	@Override
	public void process(final Open msg) {
		// nothing to do
	}

	@Override
	public void process(final Done msg) {
		final UUID orderId = msg.getOrderId();
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			workingOrder = workingOrdersByOrderId.get(orderId);
			if(workingOrder != null) {
				workingOrdersByOrderId.remove(orderId);
				workingOrders.remove(workingOrder);
				workingOrdersByClientOid.remove(workingOrder.clientOid);
			}
		}
		if(workingOrder != null) {
			final double originalSize = workingOrder.instruction.getAmount();
			final double remainingSize = msg.getRemainingSize();
			final double filledAmount = originalSize - remainingSize;
			final String originalSizeStr = gdaxDecimalFormat.format(originalSize);
			final String remainingSizeStr = gdaxDecimalFormat.format(remainingSize);
			final String filledAmountStr = gdaxDecimalFormat.format(filledAmount);
			final String timeStr = ZonedDateTime.ofInstant(
				Instant.ofEpochMilli(msg.getTimeMicros() / 1000L),
				ZoneOffset.UTC
			).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			final double filledAvgPrice = workingOrder.filledAmount == 0
				? 0.0 : workingOrder.filledAmountxPrice / workingOrder.filledAmount;
			final String filledAvgPriceStr = gdaxDecimalFormat.format(filledAvgPrice);
			final double slippage = Math.abs(workingOrder.origPrice - filledAvgPrice);
			final String slippageStr = gdaxDecimalFormat.format(slippage);

			log.info("Post order " + orderId + " was done at " + timeStr
				+ " with reason " + msg.getReason() + " filled at " + filledAvgPriceStr + " and remaining size "
				+ remainingSizeStr + ", slippage was " + slippageStr + " & original size was " + originalSizeStr);


			// Record in db and send text message
			runInBackground(() -> {
				if(msg.getRemainingSize() > 0)
					dbRecorder.recordPostOnlyCancel(msg, originalSize);

				dbRecorder.updatePostWithSlippage(workingOrder.clientOid, filledAmount, filledAvgPrice, slippage);

				final String text = (msg.getOrderSide() == OrderSide.BUY ? "Bot " : "Sold ")
					+ filledAmountStr + " " + msg.getProduct() + " at " + filledAvgPriceStr
					+ " with " + slippageStr + " slippage & " + remainingSizeStr + " remaining";
				twilioSender.sendMessage(text);
			});

		}
	}

	@Override
	public void process(final Match msg) {
		final UUID orderId = msg.getMakerOrderId();
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			workingOrder = workingOrdersByOrderId.get(orderId);
		}
		if(workingOrder != null) {
			final double size = msg.getSize();
			final double price = msg.getPrice();
			workingOrder.filledAmount += size;
			workingOrder.filledAmountxPrice += size * price;

			log.debug("FILL received for post order " + orderId + ": " + gdaxDecimalFormat.format(size)
				+ " " + msg.getOrderSide() + " " + msg.getProduct() + " at " + price);

			tactic.notifyFill(workingOrder.instruction, msg.getProduct(), msg.getOrderSide(), size, price);

			final int buySign = msg.getOrderSide() == OrderSide.BUY ? 1 : -1;
			accountant.recordTrade(Currency.USD, -buySign * size * price,
				msg.getProduct().getCryptoCurrency(), buySign * size);

			runInBackground(() -> {
				dbRecorder.recordPostOnlyFillWithSlippage(msg, workingOrder.clientOid, workingOrder.origPrice);
				accountant.refreshPositions(); // be very accurate on positions by updating after a fill
			});
		}
	}

	@Override
	public void process(final ChangeSize msg) {
		// nothing to do
	}

	@Override
	public void process(final ChangeFunds msg) {
		// nothing to do
	}

	@Override
	public void process(final Activate msg) {
		// nothing to do
	}
}
