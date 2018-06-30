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
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
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
		UUID orderId;
		double filledAmountxPrice;
		double filledAmount;
		WorkingOrderStatus status;

		WorkingOrder(final TradeInstruction instruction, final UUID clientOid, final double postedPrice) {
			this.instruction = instruction;
			this.clientOid = clientOid;
			this.postedPrice = postedPrice;
			origTimeNanos = timeKeeper.epochNanos();
			origPrice = postedPrice;
			status = POSTING;
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
		executorService = Executors.newSingleThreadExecutor();
		lockObj = new Object();
		workingOrders = new ArrayList<>();
		workingOrdersByClientOid = new HashMap<>();
		workingOrdersByOrderId = new HashMap<>();

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
		final BBO bbo = orderBookManager.getBook(product).getBBO();
		final double postedPrice = instruction.getOrderSide() == OrderSide.BUY ? bbo.bidPrice : bbo.askPrice;
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

		final CompletableFuture<OrderInfo> orderInfoFuture;
		orderInfoFuture = gdaxClient.placePostOnlyLimitOrder(product, instruction.getOrderSide(),
			instruction.getAmount(), postedPrice, clientOid, TimeUnit.HOURS);
		log.info("Placed post-only order to " + instruction + " at " + postedPrice + " with 1 hour GTT, client_oid "
			+ clientOid);

		// check for client errors and record to db
		try {
			orderInfoFuture.get();
			runInBackground(() -> dbRecorder.recordPostOnlyAttempt(instruction, clientOid, postedPrice));
		} catch(ExecutionException | InterruptedException e) {
			if(e.getCause().getClass().isAssignableFrom(GdaxClient.GdaxException.class)) {
				log.error("Could not post order to " + instruction, e.getCause());
				synchronized(lockObj) {
					workingOrders.remove(workingOrder);
					workingOrdersByClientOid.remove(clientOid);
				}
				tactic.notifyReject(instruction);
			} else {
				throw new RuntimeException(e);
			}
		}

	}

	private void cancelRepost(final WorkingOrder workingOrder, final UUID oldOrderId) {
		// TODO: Simultaneously cancel and repost if we have the funds to do so
		CompletableFuture<?> future = gdaxClient.cancelOrder(oldOrderId)
			.thenCompose(json -> {
				// refresh prices here, just before we send the order
				final BBO bbo = orderBookManager.getBook(workingOrder.instruction.getProduct()).getBBO();
				final double newPostedPrice = workingOrder.instruction.getOrderSide() == OrderSide.BUY
					? bbo.bidPrice : bbo.askPrice;
				workingOrder.postedPrice = newPostedPrice;

				log.info("Canceled, now reposting order to " + workingOrder.instruction + " at " + newPostedPrice
					+ " with 1 hour GTT, client_oid " + workingOrder.clientOid);

				final TradeInstruction instruction = workingOrder.instruction;
				return gdaxClient.placePostOnlyLimitOrder(instruction.getProduct(), instruction.getOrderSide(),
					instruction.getAmount() - workingOrder.filledAmount, newPostedPrice, workingOrder.clientOid,
					TimeUnit.HOURS);
			});

		runInBackground(() -> {
			try {
				future.get();
			} catch(ExecutionException | InterruptedException e) {
				if(e.getCause().getClass().isAssignableFrom(GdaxClient.GdaxException.class)) {
					log.error("Could not cancel/repost order to " + workingOrder.instruction, e);
					synchronized(lockObj) {
						workingOrders.remove(workingOrder);
						workingOrdersByClientOid.remove(workingOrder.clientOid);
					}
					tactic.notifyReject(workingOrder.instruction);
				} else {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private void onPriceChanged(final Product product, final double newPrice, final OrderSide side) {
		List<WorkingOrder> ordersToRepost = null;
		List<WorkingOrder> markCanceled = null;
		synchronized(lockObj) {
			if(workingOrders.isEmpty()) return;

			log.debug("Price ticked away while we have working orders, verifying " + product + " side " + side
				+ " for new top " + newPrice);

			// search working orders for any that need to be repriced or removed
			final long timeNanos = timeKeeper.epochNanos();
			for(WorkingOrder workingOrder : workingOrders) {
				// remove any orders that should have been canceled but for some reason were not marked as such
				if(workingOrder.origTimeNanos - timeNanos > ONE_HOUR_NANOS) {
					if(markCanceled == null) markCanceled = new LinkedList<>();
					markCanceled.add(workingOrder);
					continue;
				}

				final OrderSide workingOrderSide = workingOrder.instruction.getOrderSide();

				if(workingOrder.status == POSTED &&
					workingOrder.instruction.getProduct() == product &&
					workingOrderSide == side)
				{
					final boolean bidMovedAway = workingOrderSide == OrderSide.BUY
						&& workingOrder.postedPrice < newPrice - OrderBook.PRICE_EPSILON;
					final boolean askMovedAway = workingOrderSide == OrderSide.SELL
						&& workingOrder.postedPrice > newPrice + OrderBook.PRICE_EPSILON;

					if(bidMovedAway || askMovedAway) {
						if(ordersToRepost == null) ordersToRepost = new LinkedList<>();
						ordersToRepost.add(workingOrder);
					}
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
	public void onBidChanged(final Product product, final double bidPrice) {
		onPriceChanged(product, bidPrice, OrderSide.BUY);
	}

	@Override
	public void onAskChanged(final Product product, final double askPrice) {
		onPriceChanged(product, askPrice, OrderSide.SELL);
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
					+ " with " + slippage + " slippage & " + remainingSizeStr + " remaining";
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

			runInBackground(() ->
				dbRecorder.recordPostOnlyFillWithSlippage(msg, workingOrder.clientOid, workingOrder.origPrice));
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
