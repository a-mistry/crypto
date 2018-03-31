package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.time.TimeKeeper;

class ProductTracker implements GdaxMessageProcessor {
	// need to split up this class into one that tracks current interval and one that tracks history
	// these two are mixed here in a confusing way

	private final Product product;
	private final TimeKeeper timeKeeper;
	private final ProductHistory history;

	/** Current interval data */
	private IntervalData curInterval;
	/** Current interval end time in micros */
	private long intervalEndMicros;
	// extra values we need to keep track of
	/** Last trade price of previous interval */
	private double prevLastPrice;
	/** Volume times price, used for VWAP calculation */
	private double volumeTimesPrice;

	ProductTracker(Product product, TimeKeeper timeKeeper, ProductHistory history) {
		this.product = product;
		this.timeKeeper = timeKeeper;
		this.history = history;
		final long curMicros = timeKeeper.epochNanos() / 1000L;
		intervalEndMicros = calcNextIntervalMicros(curMicros);
		curInterval = new IntervalData(product, curMicros, intervalEndMicros);
		prevLastPrice = Double.NaN;
		volumeTimesPrice = 0.0;
	}

	/** Used for testing */
	protected IntervalData readCurInterval() {
		return curInterval;
	}

	final static long calcNextIntervalMicros(long curMicros) {
		return (curMicros / 1000000L / ProductHistory.INTERVAL_SECONDS + 1)
			* 1000000L * ProductHistory.INTERVAL_SECONDS;
	}

	private synchronized void recordNewOrder(final Open msg) {
		rollOverIfNeeded();
		final double size = msg.getRemainingSize();
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.newBidCount++;
			curInterval.newBidSize += size;
		} else {
			curInterval.newAskCount++;
			curInterval.newAskSize += size;
		}
	}

	private synchronized void recordCancel(final Done msg) {
		rollOverIfNeeded();
		final double size = msg.getRemainingSize();
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.bidCancelCount++;
			curInterval.bidCancelSize += size;
		} else {
			curInterval.askCancelCount++;
			curInterval.askCancelSize += size;
		}
	}

	private synchronized void recordTrade(final Match msg) {
		rollOverIfNeeded();
		final double price = msg.getPrice();
		final double size = msg.getSize();
		curInterval.lastPrice = price;
		curInterval.volume += size;
		volumeTimesPrice += price * size;
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.bidTradeCount++;
			curInterval.bidTradeSize += size;
		} else {
			curInterval.askTradeCount++;
			curInterval.askTradeSize += size;
		}
	}

	private void rollOverIfNeeded() {
		if(timeKeeper.epochNanos() / 1000L < intervalEndMicros)
			return;

		if(!Double.isNaN(prevLastPrice))
			curInterval.ret = curInterval.lastPrice / prevLastPrice - 1.0;
		curInterval.vwap = curInterval.volume > 0.0 ? volumeTimesPrice / curInterval.volume : Double.NaN;
		history.add(curInterval);

		prevLastPrice = curInterval.lastPrice;
		volumeTimesPrice = 0.0;
		final long curMicros = intervalEndMicros;
		intervalEndMicros = calcNextIntervalMicros(curMicros);
		curInterval = new IntervalData(product, curMicros, intervalEndMicros);
	}

	@Override
	public void process(final Book msg) {
		// nothing to do
	}

	@Override
	public void process(final Open msg) {
		recordNewOrder(msg);
	}

	@Override
	public void process(final Done msg) {
		if(msg.getReason() == Reason.CANCELED) {
			recordCancel(msg);
		}
	}

	@Override
	public void process(final Match msg) {
		recordTrade(msg);
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