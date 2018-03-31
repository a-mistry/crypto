package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;

public class DynamicTracker implements GdaxMessageProcessor {
	private final ProductTracker[] productTrackers;
	private final ProductHistory[] productHistories;

	public DynamicTracker(final int maxIntervals) {
		productHistories = new ProductHistory[Product.count];
		productTrackers = new ProductTracker[Product.count];
		for(Product product : Product.FAST_VALUES) {
			productHistories[product.getIndex()] = new ProductHistory(maxIntervals);
			productTrackers[product.getIndex()] = new ProductTracker();
		}
	}

	public ProductHistory getProductHistory(Product product) {
		return productHistories[product.getIndex()];
	}

	/**
	 * Records snapshot of each product's last interval data and stores it in the product history. This
	 * should be called at regular intervals to capture the history correctly.
	 */
	public void recordSnapshots() {
		for(Product product : Product.FAST_VALUES) {
			IntervalData intervalData = productTrackers[product.getIndex()].snapshot();
			productHistories[product.getIndex()].add(intervalData);
		}
	}

	@Override
	public void process(final Book msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final Open msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final Done msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final Match msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final ChangeSize msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final ChangeFunds msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(final Activate msg) {
		productTrackers[msg.getProduct().getIndex()].process(msg);
	}
}