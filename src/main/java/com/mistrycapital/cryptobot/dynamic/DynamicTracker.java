package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;

public class DynamicTracker implements GdaxMessageProcessor {
	private final ProductTracker[] productTrackers;

	public DynamicTracker() {
		productTrackers = new ProductTracker[Product.count];
		for(Product product : Product.FAST_VALUES) {
			productTrackers[product.getIndex()] = new ProductTracker();
		}
	}

	/**
	 * Records snapshot of each product's last interval data and rolls to the next interval. This
	 * should be called at regular intervals to capture the history correctly.
	 */
	public IntervalData getSnapshot(Product product) {
		return productTrackers[product.getIndex()].snapshot();
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