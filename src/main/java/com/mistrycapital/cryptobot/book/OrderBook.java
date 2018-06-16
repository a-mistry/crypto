package com.mistrycapital.cryptobot.book;

import java.util.*;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import org.slf4j.Logger;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;

public class OrderBook {
	private static final Logger log = MCLoggerFactory.getLogger();

	/** Tolerance for equating two prices */
	static final double PRICE_EPSILON = 0.00000001;

	private final TimeKeeper timeKeeper;
	private final Product product;
	private final Map<UUID,Order> activeOrders;
	private final OrderLineList bids;
	private final OrderLineList asks;
	private final Queue<Order> orderPool;
	private final BookProcessor bookProcessor;

	private final Queue<UUID> recentlyDoneQueue;
	private final Set<UUID> recentlyDoneSet;
	private static final int recentlyDoneTracked = 10000;

	private long sequence;

	public OrderBook(final TimeKeeper timeKeeper, final Product product) {
		this.timeKeeper = timeKeeper;
		this.product = product;
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

	//////////////////////////////////////////////////////
	// READ BOOK

	/**
	 * @return Top of book
	 */
	public synchronized BBO getBBO() {
		BBO bbo = new BBO();
		recordBBO(bbo);
		return bbo;
	}

	/**
	 * Records top of book to the given object
	 */
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
	private synchronized void insert(final Open msg) {
		insertNonSynchronized(msg.getOrderId(), msg.getPrice(), msg.getRemainingSize(), msg.getTimeMicros(),
			msg.getOrderSide());
	}

	/**
	 * Helper function to insert a new order.
	 * NOTE: For thread safety, this method MUST be called from a synchronized method
	 */
	private void insertNonSynchronized(final UUID orderId, final double price, final double size, final long timeMicros,
		final OrderSide side)
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
		boolean bidCrossed = order.getSide() == OrderSide.BUY && order.getPrice() >= asks.getFirstPrice();
		boolean askCrossed = order.getSide() == OrderSide.SELL && order.getPrice() <= bids.getFirstPrice();
		if(bidCrossed || askCrossed) removeLockedCrossed(order);

		// now add to map and to order line
		activeOrders.put(order.getId(), order);
		final OrderLine line;
		if(order.getSide() == OrderSide.BUY) {
			line = bids.findOrCreate(order.getPrice());
		} else {
			line = asks.findOrCreate(order.getPrice());
		}
		order.setLine(line);
	}

	/**
	 * Remove any resting orders that cross with the given order (since they are invalid). This scenario
	 * can happen with gaps in data because by the time we receive book data, it is stale compared to the websocket
	 * feed
	 */
	private void removeLockedCrossed(final Order order) {
		log.debug("Removing locked/crossed orders on " + product + " at " + timeKeeper.iso8601() + " " + order.getId()
			+ " " + order.getSide() + " " + order.getPrice()
			+ " bidSize=" + bids.getFirstSize() + " askSize=" + asks.getFirstSize());

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

			bidCrossed = order.getSide() == OrderSide.BUY && order.getPrice() >= asks.getFirstPrice();
			askCrossed = order.getSide() == OrderSide.SELL && order.getPrice() <= bids.getFirstPrice();
		} while(bidCrossed || askCrossed);
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
	private synchronized void remove(final UUID orderId) {
		// make sure we don't add this order anytime soon (for example if we rebuild the book from stale data)
		while(recentlyDoneQueue.size() >= recentlyDoneTracked) {
			UUID oldestId = recentlyDoneQueue.poll();
			recentlyDoneSet.remove(oldestId);
		}
		recentlyDoneSet.add(orderId);
		recentlyDoneQueue.offer(orderId);

		final Order order = activeOrders.get(orderId);
		if(order != null) {
			activeOrders.remove(orderId);
			order.destroy();
			orderPool.add(order);
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
		for(Book.Order bookOrder : book.getBids()) {
			insertNonSynchronized(bookOrder.orderId, bookOrder.price, bookOrder.size, book.getTimeMicros(),
				OrderSide.BUY);
		}
		for(Book.Order bookOrder : book.getAsks()) {
			insertNonSynchronized(bookOrder.orderId, bookOrder.price, bookOrder.size, book.getTimeMicros(),
				OrderSide.SELL);
		}
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