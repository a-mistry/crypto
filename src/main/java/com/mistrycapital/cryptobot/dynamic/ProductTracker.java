package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.util.Iterator;

public class ProductTracker implements GdaxMessageProcessor {
	private static final int BUFFER_SIZE = DynamicTracker.SECONDS_TO_KEEP / DynamicTracker.INTERVAL_SECONDS;

	private final Product product;
	private final TimeKeeper timeKeeper;
	/** Recorded data for each interval, in a circular buffer with the head containing the oldest value */
	private final IntervalData[] intervalData;
	/** Index of first value with valid data */
	private int head;
	/** Index of next value we are currently writing to */
	private int next;
	/** Current interval data */
	private IntervalData curInterval;

	// extra values we need to keep track of
	/** Last trade price of previous interval */
	private double prevLastPrice;
	/** Volume times price, used for VWAP calculation */
	private double volumeTimesPrice;

	ProductTracker(Product product, TimeKeeper timeKeeper) {
		this.product = product;
		this.timeKeeper = timeKeeper;
		intervalData = new IntervalData[BUFFER_SIZE];
		for(int i = 0; i < intervalData.length; i++)
			intervalData[i] = new IntervalData();
		head = -1;
		next = 0;
		curInterval = intervalData[next];
		curInterval.reset(timeKeeper.epochNanos() / 1000L);
		prevLastPrice = Double.NaN;
		volumeTimesPrice = 0.0;
	}

	/**
	 * @return Latest recorded interval value (not the interval we are currently tracking)
	 */
	public synchronized IntervalData latest() {
		return head == -1 ? null : intervalData[(next - 1) % BUFFER_SIZE];
	}

	/**
	 * @return The full history we are storing, sorted from latest to oldest
	 */
	public synchronized Iterable<IntervalData> values() {
		return new BufferIterable();
	}

	private class BufferIterable implements Iterable<IntervalData> {
		@Override
		public Iterator<IntervalData> iterator() {
			return new BufferIterator((next - 1) % BUFFER_SIZE, head);
		}
	}

	private class BufferIterator implements Iterator<IntervalData> {
		private int index = (next - 1) % BUFFER_SIZE;
		private final int head;

		BufferIterator(int index, int head) {
			this.index = index;
			this.head = head;
		}

		@Override
		public boolean hasNext() {
			// should probably add a check here to see if the indices are still valid

			if(head == -1)
				return false;

			return index != head;
		}

		@Override
		public IntervalData next() {
			if(!hasNext()) return null;

			IntervalData retVal = intervalData[index];
			index = (index - 1) % BUFFER_SIZE;
			return retVal;
		}
	}

	private synchronized void recordNewOrder(final Open msg) {
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

	private void rollOver() {
		if((head + 1) % BUFFER_SIZE == next)
			head = (head + 1) % BUFFER_SIZE;

		if(!Double.isNaN(prevLastPrice))
			curInterval.ret = curInterval.lastPrice / prevLastPrice - 1.0;
		curInterval.vwap = curInterval.volume > 0.0 ? volumeTimesPrice / curInterval.volume : Double.NaN;

		prevLastPrice = curInterval.lastPrice;
		volumeTimesPrice = 0.0;
		next = (next + 1) % BUFFER_SIZE;
		curInterval = intervalData[next];
		curInterval.reset(timeKeeper.epochNanos() / 1000L);
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