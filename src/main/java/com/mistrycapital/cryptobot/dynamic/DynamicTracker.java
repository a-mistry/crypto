package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.time.TimeKeeper;

public class DynamicTracker implements GdaxMessageProcessor {

	private final TimeKeeper timeKeeper;
	private final ProductTracker[] productTrackers;
	private final ProductHistory[] productHistories;

	public DynamicTracker(TimeKeeper timeKeeper, FileAppender intervalFileAppender) {
		this.timeKeeper = timeKeeper;
		productHistories = new ProductHistory[Product.count];
		productTrackers = new ProductTracker[Product.count];
		for(Product product : Product.FAST_VALUES) {
			productHistories[product.getIndex()] = new ProductHistory(product, intervalFileAppender);
			productTrackers[product.getIndex()] =
				new ProductTracker(product, timeKeeper, productHistories[product.getIndex()]);
		}
	}

	public ProductHistory getProductHistory(Product product) {
		return productHistories[product.getIndex()];
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