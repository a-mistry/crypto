package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.time.TimeKeeper;

public class ProductTracker implements GdaxMessageProcessor {
	private static final int BUFFER_SIZE = DynamicTracker.SECONDS_TO_KEEP / DynamicTracker.INTERVAL_SECONDS;

	private final Product product;
	private final TimeKeeper timeKeeper;
	private final IntervalData[] intervalData;
	private int head;

	public ProductTracker(Product product, TimeKeeper timeKeeper) {
		this.product = product;
		this.timeKeeper = timeKeeper;
		intervalData = new IntervalData[BUFFER_SIZE];
		for(int i=0; i<intervalData.length; i++)
			intervalData[i] = new IntervalData();
		head = 0;
		resetInterval();
	}

	// current interval data
	double volume;
	double volumeTimesPrice;
	double lastTrade;

	private void resetInterval() {
		volume = 0.0;
		volumeTimesPrice = 0.0;
		lastTrade = 0.0;
	}

	@Override
	public void process(final Book msg) {
		// nothing to do
	}

	@Override
	public void process(final Open msg) {
		// record new msg
	}

	@Override
	public void process(final Done msg) {
		// record cancels
	}

	@Override
	public void process(final Match msg) {
		// record trade price
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