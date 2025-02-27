package com.mistrycapital.cryptobot.book;

import java.util.*;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import org.slf4j.Logger;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;

public class OrderBook implements BBOProvider {
	private static final Logger log = MCLoggerFactory.getLogger();

	/** Tolerance for equating two prices */
	public static final double PRICE_EPSILON = 0.00000001;

	private final TimeKeeper timeKeeper;
	private final Product product;
	private final Map<UUID,Order> activeOrders;
	private final OrderLineList bids;
	private final OrderLineList asks;
	private final Queue<Order> orderPool;
	private final BookProcessor bookProcessor;
	private TopOfBookSubscriber[] topOfBookSubscribers;

	private final Queue<UUID> recentlyDoneQueue;
	private final Set<UUID> recentlyDoneSet;
	private static final int recentlyDoneTracked = 10000;

	private long sequence;

	public OrderBook(final TimeKeeper timeKeeper, final Product product) {
		this.timeKeeper = timeKeeper;
		this.product = product;
		topOfBookSubscribers = new TopOfBookSubscriber[0];
		bids = new OrderLineList(false);
		asks = new OrderLineList(true);
		activeOrders = new HashMap<>(10000);
		orderPool = new ArrayDeque<>(1000);
		for(int i = 0; i < 1000; i++) {
			orderPool.add(new Order());
		}
		bookProcessor = new BookProcessor();

		recentlyDoneQueue = new ArrayDeque<>(recentlyDoneTracked);
		recentlyDoneSet = new HashSet<>(recentlyDoneTracked);
	}

	public GdaxMessageProcessor getBookProcessor() {
		return bookProcessor;
	}

	/** Adds a subscriber that will listen for top of book updates */
	public void subscribe(TopOfBookSubscriber topOfBookSubscriber) {
		TopOfBookSubscriber[] newSubscribers = new TopOfBookSubscriber[topOfBookSubscribers.length + 1];
		System.arraycopy(topOfBookSubscribers, 0, newSubscribers, 0, topOfBookSubscribers.length);
		newSubscribers[newSubscribers.length - 1] = topOfBookSubscriber;
		topOfBookSubscribers = newSubscribers;
	}

	//////////////////////////////////////////////////////
	// READ BOOK

	/**
	 * Records top of book to the given object
	 */
	@Override
	public synchronized void recordBBO(BBO bbo) {
		bbo.reset(
			bids.getFirstPrice(),
			asks.getFirstPrice(),
			bids.getFirstSize(),
			asks.getFirstSize()
		);
	}

	/**
	 * @return Number of bid orders
	 */
	public synchronized int getBidCount() {
		return bids.getCountBeforePrice(0.0);
	}

	/**
	 * @return Number of ask orders
	 */
	public synchronized int getAskCount() {
		return asks.getCountBeforePrice(Double.MAX_VALUE);
	}

	/**
	 * @return Number of bids whose price is greater than or equal to the given price
	 */
	public synchronized int getBidCountGEPrice(double price) {
		return bids.getCountBeforePrice(price);
	}

	/**
	 * @return Number of asks whose price is lower than or equal to the given price
	 */
	public synchronized int getAskCountLEPrice(double price) {
		return asks.getCountBeforePrice(price);
	}

	/**
	 * @return Total size of bids
	 */
	public synchronized double getBidSize() {
		return bids.getSizeBeforePrice(0.0);
	}

	/**
	 * @return Total size of asks
	 */
	public synchronized double getAskSize() {
		return asks.getSizeBeforePrice(Double.MAX_VALUE);
	}

	/**
	 * @return Total size of bids whose price is greater than or equal to the given price
	 */
	public synchronized double getBidSizeGEPrice(double price) {
		return bids.getSizeBeforePrice(price);
	}

	/**
	 * @return Total size of asks whose price is lower than or equal to the given price
	 */
	public synchronized double getAskSizeLEPrice(double price) {
		return asks.getSizeBeforePrice(price);
	}

	/**
	 * Record top of book, depth of book, and weighted mid prices. For depth, takes as input an array of objects.
	 * For each object, fills in the counts and sizes of orders that are within the given percentage from the midpoint.
	 * For weighted mids, takes as input an array of objects containing the number of levels to use.
	 * <p>
	 * This is a significantly more efficient method for getting multiple pieces of book data than querying
	 * individually since the synchronization cost is paid once.
	 *
	 * @param bbo    Best bid/offer object to record
	 * @param depths Array of depth objects with the pctFromMid given. The remaining fields will be filled
	 */
	public synchronized void recordDepthsAndBBO(BBO bbo, Depth[] depths, WeightedMid[] mids) {
		recordBBO(bbo);

		final double mid = bbo.midPrice();
		for(int i = 0; i < depths.length; i++) {
			final Depth depth = depths[i];
			final double pct = depth.pctFromMid;
			final double bidThreshold = mid * (1.0 - pct);
			final double askThreshold = mid * (1.0 + pct);
			depth.reset(
				bids.getCountBeforePrice(bidThreshold),
				asks.getCountBeforePrice(askThreshold),
				bids.getSizeBeforePrice(bidThreshold),
				asks.getSizeBeforePrice(askThreshold)
			);
		}

		// garbage free calc
		if(mids != null)
			for(int i = 0; i < mids.length; i++) {
				bids.calcWeightedAvgPrice(mids[i]);
				double size = mids[i].size;
				double priceSize = mids[i].weightedMidPrice * size;
				asks.calcWeightedAvgPrice(mids[i]);
				priceSize += mids[i].weightedMidPrice * mids[i].size;
				size += mids[i].size;
				mids[i].weightedMidPrice = priceSize / size;
				mids[i].size = size;
			}
	}

	/**
	 * @return Snapshot of the order book, JSON encoded as would be given by a level 3
	 * gdax query. This can be used to save a simulated gdax level 3 query in data logs
	 */
	public synchronized String getGdaxSnapshot() {
		final StringBuilder bids = new StringBuilder();
		boolean firstBid = true;
		final StringBuilder asks = new StringBuilder();
		boolean firstAsk = true;
		for(Order order : activeOrders.values()) {
			final StringBuilder sideBuilder;
			if(order.getSide() == OrderSide.BUY) {
				if(firstBid) {
					firstBid = false;
				} else {
					bids.append(',');
				}
				sideBuilder = bids;
			} else {
				if(firstAsk) {
					firstAsk = false;
				} else {
					asks.append(',');
				}
				sideBuilder = asks;
			}
			sideBuilder.append('[');
			sideBuilder.append(order.getPrice());
			sideBuilder.append(',');
			sideBuilder.append(order.getSize());
			sideBuilder.append(',');
			sideBuilder.append('\"');
			sideBuilder.append(order.getId().toString());
			sideBuilder.append('\"');
			sideBuilder.append(']');
		}
		StringBuilder builder = new StringBuilder();
		builder.append("{\"asks\":[");
		builder.append(asks);
		builder.append("],\"bids\":[");
		builder.append(bids);
		builder.append("],\"product_id\":\"");
		builder.append(product.toString());
		builder.append("\",\"sequence\":");
		builder.append(sequence);
		builder.append(",\"time\":\"");
		builder.append(timeKeeper.iso8601());
		builder.append("\",\"type\":\"book\"}");
		return builder.toString();
	}

	//////////////////////////////////////////////////////
	// MODIFY BOOK

	/**
	 * Inserts a new order into the book from the given Open message
	 */
	private void insert(final Open msg) {
		final OrderLine orderLine;
		final double topPrice; // top of book price before this insert
		final double msgPrice = msg.getPrice();
		final boolean isBuy = msg.getOrderSide() == OrderSide.BUY;

		synchronized(this) {
			if(isBuy) {
				topPrice = bids.getFirstPrice();
				orderLine = bids.findOrCreate(msgPrice);
			} else {
				topPrice = asks.getFirstPrice();
				orderLine = asks.findOrCreate(msgPrice);
			}
			insertNonSynchronized(msg.getOrderId(), msgPrice, msg.getRemainingSize(), msg.getTimeMicros(),
				msg.getOrderSide(), orderLine);
		}

		// check if insert caused new top of book and fire subscribers
		// this is done outside of synchronization
		final boolean isNewTop = (isBuy && msgPrice > topPrice + PRICE_EPSILON)
			|| (!isBuy && msgPrice < topPrice - PRICE_EPSILON);
		if(isNewTop || Double.isNaN(topPrice)) {
			if(isBuy) {
				final double askPrice = asks.getFirstPrice();
				for(TopOfBookSubscriber subscriber : topOfBookSubscribers)
					subscriber.onChanged(product, OrderSide.BUY, msgPrice, askPrice);
			} else {
				final double bidPrice = bids.getFirstPrice();
				for(TopOfBookSubscriber subscriber : topOfBookSubscribers)
					subscriber.onChanged(product, OrderSide.SELL, bidPrice, msgPrice);
			}
		}
	}

	/**
	 * Helper function to insert a new order.
	 * NOTE: For thread safety, this method MUST be called from a synchronized method
	 */
	private void insertNonSynchronized(final UUID orderId, final double price, final double size, final long timeMicros,
		final OrderSide side, final OrderLine orderLine)
	{
		// if we've seen this order (maybe because of rebuilding the book), skip it
		if(recentlyDoneSet.contains(orderId)) return;

		// get order object first
		if(orderPool.isEmpty()) {
			orderPool.add(new Order());
		}
		Order order = orderPool.remove();
		order.reset(orderId, price, size, timeMicros, side);

		// if we see locked/crossed markets, remove resting orders as they are likely invalid
		boolean bidCrossed =
			order.getSide() == OrderSide.BUY && order.getPrice() > asks.getFirstPrice() + PRICE_EPSILON;
		boolean askCrossed =
			order.getSide() == OrderSide.SELL && order.getPrice() < bids.getFirstPrice() - PRICE_EPSILON;
		if(bidCrossed || askCrossed) removeLockedCrossed(order);

		// now add to map and to order line
		activeOrders.put(order.getId(), order);
		order.setLine(orderLine);
	}

	/**
	 * Remove any resting orders that cross with the given order (since they are invalid). This scenario
	 * can happen with gaps in data because by the time we receive book data, it is stale compared to the websocket
	 * feed
	 */
	private void removeLockedCrossed(final Order order) {
		log.debug("Removing locked/crossed orders on " + product + " at " + timeKeeper.iso8601() + " " + order.getId()
			+ " " + order.getSide() + " " + order.getPrice()
			+ " TOB " + bids.getFirstPrice() + "(" + bids.getFirstSize()
			+ ")-" + asks.getFirstPrice() + "(" + asks.getFirstSize() + ")");

		int levelsCleared = 0;
		boolean bidCrossed;
		boolean askCrossed;
		do {
			final List<Order> toRemove = order.getSide() == OrderSide.BUY
				? asks.getFirstOrders() : bids.getFirstOrders();

			for(Order resting : toRemove) {
				log.debug("Removed " + resting.getId() + " " + resting.getPrice());
				activeOrders.remove(resting.getId());
				resting.destroy();
				orderPool.add(resting);
			}

			levelsCleared++;
			if(levelsCleared > 100) {
				log.error("Found 100+ levels in locked/crossed order removal");
				break; // in case of bug, don't loop forever
			}

			bidCrossed = order.getSide() == OrderSide.BUY && order.getPrice() > asks.getFirstPrice() + PRICE_EPSILON;
			askCrossed = order.getSide() == OrderSide.SELL && order.getPrice() < bids.getFirstPrice() - PRICE_EPSILON;
		} while(bidCrossed || askCrossed);

		if(levelsCleared > 0) {
			log.error("Cleared " + levelsCleared + " levels in locked/crossed order removal");
		}
	}

	/**
	 * Modifies a given order's size in the book
	 */
	private synchronized void changeSize(final UUID orderId, final double newSize) {
		final Order order = activeOrders.get(orderId);
		if(order != null) {
			order.changeSize(newSize);
		}
	}

	/**
	 * Removes a given order from the book
	 */
	private void remove(final UUID orderId) {
		final double prevTopPrice;
		final double newTopPrice;
		final boolean isBuy;

		synchronized(this) {
			// make sure we don't add this order anytime soon (for example if we rebuild the book from stale data)
			while(recentlyDoneQueue.size() >= recentlyDoneTracked) {
				UUID oldestId = recentlyDoneQueue.poll();
				recentlyDoneSet.remove(oldestId);
			}
			recentlyDoneSet.add(orderId);
			recentlyDoneQueue.offer(orderId);

			final Order order = activeOrders.get(orderId);
			if(order == null) {
				prevTopPrice = newTopPrice = 0.0; // these must be the same
				isBuy = true; // doesn't matter what this is
			} else {
				// need to check if removing this order will change the top of book and fire subscriptions if so
				isBuy = order.getSide() == OrderSide.BUY;
				prevTopPrice = isBuy ? bids.getFirstPrice() : asks.getFirstPrice();

				activeOrders.remove(orderId);
				order.destroy();
				orderPool.add(order);

				newTopPrice = isBuy ? bids.getFirstPrice() : asks.getFirstPrice();
			}
		}

		// trigger subscriptions outside of synchronized block
		if(Math.abs(newTopPrice - prevTopPrice) > PRICE_EPSILON) {
			if(isBuy) {
				final double askPrice = asks.getFirstPrice();
				for(TopOfBookSubscriber subscriber : topOfBookSubscribers)
					subscriber.onChanged(product, OrderSide.BUY, newTopPrice, askPrice);
			} else {
				final double bidPrice = bids.getFirstPrice();
				for(TopOfBookSubscriber subscriber : topOfBookSubscribers)
					subscriber.onChanged(product, OrderSide.SELL, bidPrice, newTopPrice);
			}
		}
	}

	/**
	 * Clears out any existing values and rebuilds the book from the given Book message
	 */
	private synchronized void rebuild(final Book book) {
		// clear existing book
		for(Order order : activeOrders.values()) {
			order.destroy();
			orderPool.add(order);
		}
		activeOrders.clear();
		bids.clear();
		asks.clear();

		// note that since level 3 does not give us the times, we will use the time of book message as a best
		// approximation
		// memoize lines to save computation
		Map<Double,OrderLine> orderLineMap = new HashMap<>(1000);
		for(final Book.Order bookOrder : book.getBids()) {
			OrderLine orderLine =
				orderLineMap.computeIfAbsent(bookOrder.price, bids::findOrCreate);
			insertNonSynchronized(bookOrder.orderId, bookOrder.price, bookOrder.size, book.getTimeMicros(),
				OrderSide.BUY, orderLine);
		}
		orderLineMap.clear();
		for(Book.Order bookOrder : book.getAsks()) {
			OrderLine orderLine =
				orderLineMap.computeIfAbsent(bookOrder.price, asks::findOrCreate);
			insertNonSynchronized(bookOrder.orderId, bookOrder.price, bookOrder.size, book.getTimeMicros(),
				OrderSide.SELL, orderLine);
		}
		orderLineMap = null; // mark for GC

		// We may have tried to add orders that were in the previously removed set and hence were not actually added
		// This creates the possibility of having lines with no orders
		// Remove these lines here to avoid locked/crossed markets
		bids.removeEmptyLines();
		asks.removeEmptyLines();
	}

	@Override
	public String toString() {
		return product + " Book";
	}

	/**
	 * Assumes messages are for this book's product
	 */
	class BookProcessor implements GdaxMessageProcessor {
		@Override
		public void process(Book msg) {
			sequence = msg.getSequence();
			rebuild(msg);
			log.info("Built book for " + product);
		}

		@Override
		public void process(Received msg) {
			// nothing to do
			sequence = msg.getSequence();
		}

		@Override
		public void process(Open msg) {
			sequence = msg.getSequence();
			insert(msg);
		}

		@Override
		public void process(Done msg) {
			sequence = msg.getSequence();
			remove(msg.getOrderId());
		}

		@Override
		public void process(Match msg) {
			// nothing to do
			sequence = msg.getSequence();
		}

		@Override
		public void process(ChangeSize msg) {
			sequence = msg.getSequence();
			changeSize(msg.getOrderId(), msg.getNewSize());
		}

		@Override
		public void process(ChangeFunds msg) {
			// nothing to do
			sequence = msg.getSequence();
		}

		@Override
		public void process(Activate msg) {
			// nothing to do
			sequence = msg.getSequence();
		}
	}
}