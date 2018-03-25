package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.time.TimeKeeper;

public class DynamicTracker implements GdaxMessageProcessor {
	static final int SECONDS_TO_KEEP = 60 * 60 * 24; // keep one day
	static final int INTERVAL_SECONDS = 60;

	private final TimeKeeper timeKeeper;
	private final ProductTracker[] productTrackers;

	public DynamicTracker(TimeKeeper timeKeeper) {
		this.timeKeeper = timeKeeper;
		productTrackers = new ProductTracker[Product.count];
		for(Product product : Product.FAST_VALUES) {
			productTrackers[product.getIndex()] = new ProductTracker(product, timeKeeper);
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