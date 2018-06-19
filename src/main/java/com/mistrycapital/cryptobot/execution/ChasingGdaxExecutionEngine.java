package com.mistrycapital.cryptobot.execution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.tactic.Tactic;
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
public class ChasingGdaxExecutionEngine implements ExecutionEngine, GdaxMessageProcessor {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final OrderBookManager orderBookManager;
	private final Accountant accountant;
	private final Tactic tactic;
	private final DBRecorder dbRecorder;
	private final TwilioSender twilioSender;
	private final GdaxClient gdaxClient;
	private final ExecutorService executorService;
	private final Object lockObj;
	/** Client oids and orders that have been sent to gdax but we do not have an order id yet */
	private final Map<UUID,WorkingOrder> postedClientOids;
	/** All working orders by product */
	private final List<List<WorkingOrder>> workingOrdersByProduct;
	/** Working orders that have been received by gdax and have an order id */
	private final Map<UUID,WorkingOrder> workingOrdersById;
	/** Current view of prices. May be stale to the outside, not inside */
	private final BBO[] workingPricesByProduct;

	enum WorkingOrderStatus {
		POSTING,
		POSTED
	}

	class WorkingOrder {
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
			origPrice = postedPrice;
			status = POSTING;
		}
	}

	public ChasingGdaxExecutionEngine(Accountant accountant, OrderBookManager orderBookManager, DBRecorder dbRecorder,
		TwilioSender twilioSender, Tactic tactic, GdaxClient gdaxClient)
	{
		this.orderBookManager = orderBookManager;
		this.accountant = accountant;
		this.tactic = tactic;
		this.dbRecorder = dbRecorder;
		this.twilioSender = twilioSender;
		this.gdaxClient = gdaxClient;
		executorService = Executors.newSingleThreadExecutor();
		lockObj = new Object();
		workingOrdersByProduct = new ArrayList<>(Product.count);
		for(int i = 0; i < Product.count; i++)
			workingOrdersByProduct.add(new ArrayList<>());
		postedClientOids = new HashMap<>();
		workingOrdersById = new HashMap<>();
		workingPricesByProduct = new BBO[Product.count];
		for(int i = 0; i < workingPricesByProduct.length; i++) {
			workingPricesByProduct[i] = new BBO();
			workingPricesByProduct[i].bidPrice = workingPricesByProduct[i].askPrice = Double.NaN;
		}

		// cancel any outstanding orders on reset
		gdaxClient.cancelAll()
			.thenAccept(json -> {
				JsonArray jsonArray = json.getAsJsonArray();
				for(JsonElement element : jsonArray) {
					log.info("On execution engine restart canceled order " + element.getAsString());
					// TODO: should log this to db
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

	private void post(TradeInstruction instruction) {
		log.debug("Posting instruction " + instruction);
		final Product product = instruction.getProduct();
		final UUID clientOid = UUID.randomUUID();
		// Need to refresh quotes here since they may have moved since we last had a working order
		final BBO bbo = workingPricesByProduct[product.getIndex()];
		orderBookManager.getBook(product).recordBBO(bbo);
		final double postedPrice = instruction.getOrderSide() == OrderSide.BUY ? bbo.bidPrice : bbo.askPrice;
		if(Double.isNaN(postedPrice)) {
			log.error("Tried to post order to " + instruction + " but top of book is NaN so we can't determine price");
			tactic.notifyReject(instruction);
			return;
		}
		final List<WorkingOrder> workingOrderList = workingOrdersByProduct.get(product.getIndex());
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			workingOrder = new WorkingOrder(instruction, clientOid, postedPrice);
			workingOrderList.add(workingOrder);
			postedClientOids.put(clientOid, workingOrder);
		}

		final CompletableFuture<OrderInfo> orderInfoFuture;
		orderInfoFuture = gdaxClient.placePostOnlyLimitOrder(product, instruction.getOrderSide(),
			instruction.getAmount(), postedPrice, clientOid, TimeUnit.DAYS);
		log.info("Placed post-only order to " + instruction + " at " + postedPrice + " with 1 day GTT, client_oid "
			+ clientOid);

		runInBackground(() -> dbRecorder.recordPostOnlyAttempt(instruction, clientOid, postedPrice));

		// check for client errors
		try {
			orderInfoFuture.get();
		} catch(ExecutionException | InterruptedException e) {
			if(e.getCause().getClass().isAssignableFrom(GdaxClient.GdaxException.class)) {
				log.error("Could not post order to " + instruction, e);
				synchronized(lockObj) {
					workingOrderList.remove(workingOrder);
					postedClientOids.remove(clientOid);
				}
				tactic.notifyReject(instruction);
			} else {
				throw new RuntimeException(e);
			}
		}

	}

	/**
	 * Runs through working orders for the product and for any order where the price has moved away from the posted
	 * price, cancels the order and reposts it at the new top of book
	 */
	private void cancelRepostOrders(Product product, BBO bbo) {
		List<WorkingOrder> workingOrderList = workingOrdersByProduct.get(product.getIndex());
		List<WorkingOrder> cancelRepostList = null;
		synchronized(lockObj) {
			if(workingOrderList.isEmpty()) return;

			log.debug("Price ticked away while we have working orders, verifying " + product
				+ " (" + bbo.bidPrice + "-" + bbo.askPrice + ")");

			for(WorkingOrder workingOrder : workingOrderList) {
				final OrderSide orderSide = workingOrder.instruction.getOrderSide();
				boolean repriceOnBid =
					orderSide == OrderSide.BUY && workingOrder.postedPrice < bbo.bidPrice - OrderBook.PRICE_EPSILON;
				boolean repriceOnAsk =
					orderSide == OrderSide.SELL && workingOrder.postedPrice > bbo.askPrice + OrderBook.PRICE_EPSILON;
				if(repriceOnBid || repriceOnAsk) {
					if(cancelRepostList == null) cancelRepostList = new LinkedList<>();
					cancelRepostList.add(workingOrder);
				}
			}
		}
		if(cancelRepostList != null) {
			for(WorkingOrder workingOrder : cancelRepostList)
				if(workingOrder.status != POSTING)
					cancelRepost(workingOrder, bbo);
		}
	}

	private void cancelRepost(final WorkingOrder workingOrder, final BBO bbo) {
		if(workingOrder.orderId == null) {
			log.error("Supposed to reprice order for " + workingOrder.instruction + " but working order has no id");
			return;
		}

		workingOrder.status = POSTING;
		final double oldPostedPrice = workingOrder.postedPrice;

		log.info("Cancel/reposting order to " + workingOrder.instruction + " old price " + oldPostedPrice + " line "
			+ bbo.bidPrice + "-" + bbo.askPrice + " with 1 day GTT, client_oid " + workingOrder.clientOid);

		CompletableFuture<OrderInfo> future = gdaxClient.cancelOrder(workingOrder.orderId)
			.thenCompose(json -> {
				synchronized(lockObj) {
					postedClientOids.put(workingOrder.clientOid, workingOrder);
				}

				// refresh prices here, just before we send the order
				final double newPostedPrice = workingOrder.instruction.getOrderSide() == OrderSide.BUY
					? bbo.bidPrice : bbo.askPrice;
				workingOrder.postedPrice = newPostedPrice;

				log.info("Canceled, now reposting order to " + workingOrder.instruction + " at " + newPostedPrice
					+ " with 1 day GTT, client_oid " + workingOrder.clientOid);
				runInBackground(() ->
					twilioSender.sendMessage("Reposting order to " + workingOrder.instruction + " from "
						+ gdaxDecimalFormat.format(oldPostedPrice) + " to " +
						gdaxDecimalFormat.format(newPostedPrice)));

				final TradeInstruction instruction = workingOrder.instruction;
				return gdaxClient.placePostOnlyLimitOrder(instruction.getProduct(), instruction.getOrderSide(),
					instruction.getAmount(), newPostedPrice, workingOrder.clientOid, TimeUnit.DAYS);
			});

		try {
			future.get();
			// TODO: record cancel/repost to db

		} catch(ExecutionException | InterruptedException e) {
			if(e.getCause().getClass().isAssignableFrom(GdaxClient.GdaxException.class)) {
				log.error("Could not cancel/repost order to " + workingOrder.instruction, e);
				synchronized(lockObj) {
					workingOrdersByProduct.get(workingOrder.instruction.getProduct().getIndex()).remove(workingOrder);
					workingOrdersById.remove(workingOrder.orderId);
				}
				tactic.notifyReject(workingOrder.instruction);
				// TODO: should log this to db
			} else {
				throw new RuntimeException(e);
			}
		}
	}

	/** Run task in the background - used to run DB queries / twilio calls outside of this thread */
	private void runInBackground(Runnable runnable) {
		executorService.submit(runnable);
	}

	@Override
	public void process(final Book msg) {
		// when we first start, prices are NaN, but this message means the order book should have the info
		final OrderBook orderBook = orderBookManager.getBook(msg.getProduct());
		final BBO bbo = workingPricesByProduct[msg.getProduct().getIndex()];
		orderBook.recordBBO(bbo);
	}

	@Override
	public void process(final Received msg) {
		final UUID clientOid = msg.getClientOid();
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			workingOrder = postedClientOids.get(clientOid);
			if(workingOrder != null) {
				postedClientOids.remove(clientOid);
				workingOrdersById.put(msg.getOrderId(), workingOrder);
			}
		}
		if(workingOrder != null) {
			workingOrder.orderId = msg.getOrderId();
			workingOrder.status = POSTED;
			log.debug("Got RECEIVED message for post order to " + workingOrder.instruction + " client_oid " + clientOid
				+ " maps to gdax order_id: " + workingOrder.orderId);
			runInBackground(() -> dbRecorder.updatePostOnlyId(clientOid, workingOrder.orderId));
		}
	}

	@Override
	public void process(final Open msg) {
		final Product product = msg.getProduct();
		final BBO bbo = workingPricesByProduct[product.getIndex()];
		if(msg.getOrderSide() == OrderSide.BUY && msg.getPrice() > bbo.bidPrice + OrderBook.PRICE_EPSILON) {
			bbo.bidPrice = msg.getPrice();
			cancelRepostOrders(product, bbo);
		} else if(msg.getOrderSide() == OrderSide.SELL && msg.getPrice() < bbo.askPrice - OrderBook.PRICE_EPSILON) {
			bbo.askPrice = msg.getPrice();
			cancelRepostOrders(product, bbo);
		}
	}

	@Override
	public void process(final Done msg) {
		final UUID orderId = msg.getOrderId();
		final Product product = msg.getProduct();
		final WorkingOrder workingOrder;
		synchronized(lockObj) {
			// don't consider "done" if we are cancel/reposting
			WorkingOrder temp = workingOrdersById.get(orderId);
			workingOrder = temp != null && temp.status == POSTED ? temp : null;
			if(workingOrder != null) {
				workingOrdersById.remove(orderId);
				workingOrdersByProduct.get(product.getIndex()).remove(workingOrder);
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

				//TODO: record post finished, slippage amount

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
			workingOrder = workingOrdersById.get(orderId);
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

			runInBackground(() -> dbRecorder.recordPostOnlyFill(msg));
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
