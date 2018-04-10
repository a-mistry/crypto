package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

/**
 * Snapshot of all product data as of the end of an interval
 */
public class ConsolidatedSnapshot {
	private final ProductSnapshot[] productSnapshots;
	private final long timeNanos;

	public ConsolidatedSnapshot(final ProductSnapshot[] productSnapshots, long timeNanos) {
		this.productSnapshots = productSnapshots;
		this.timeNanos = timeNanos;
	}

	/**
	 * Snapshots book data and interval data. This has the side effect of rolling to the next interval
	 * in the dynamic tracker
	 * @return Snapshot of all product data as of now (the end of the interval)
	 */
	public static final ConsolidatedSnapshot getSnapshot(final OrderBookManager orderBookManager,
		final DynamicTracker dynamicTracker, final TimeKeeper timeKeeper)
	{
		final ProductSnapshot[] productSnapshots = new ProductSnapshot[Product.count];
		for(Product product : Product.FAST_VALUES) {
			final OrderBook orderBook = orderBookManager.getBook(product);
			final IntervalData intervalData = dynamicTracker.getSnapshot(product);
			productSnapshots[product.getIndex()] = ProductSnapshot.getSnapshot(product, orderBook, intervalData);
		}
		return new ConsolidatedSnapshot(productSnapshots, timeKeeper.epochNanos());
	}

	public ProductSnapshot getProductSnapshot(Product product) {
		return productSnapshots[product.getIndex()];
	}

	public long getTimeNanos() {
		return timeNanos;
	}
}