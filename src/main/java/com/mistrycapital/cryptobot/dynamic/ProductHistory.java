package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.Product;

import java.util.ConcurrentModificationException;
import java.util.Iterator;

/**
 * Circular buffer of interval data
 */
public class ProductHistory {
	private static final int BUFFER_SIZE = DynamicTracker.SECONDS_TO_KEEP / DynamicTracker.INTERVAL_SECONDS;

	private final Product product;
	/** Recorded data for each interval, in a circular buffer with the head containing the oldest value */
	private final IntervalData[] intervalData;
	/** Index of oldest value */
	private int oldest;
	/** Index of last value */
	private int last;

	ProductHistory(Product product) {
		this.product = product;
		intervalData = new IntervalData[BUFFER_SIZE];
		for(int i = 0; i < intervalData.length; i++)
			intervalData[i] = new IntervalData();
		oldest = -1;
		last = -1;
	}

	public synchronized void add(IntervalData newData) {
		last = (last + 1) % BUFFER_SIZE;
		if(oldest == -1 || last == oldest) {
			// need to move oldest
			oldest = (oldest + 1) % BUFFER_SIZE;
		}
		intervalData[last].copyFrom(newData);
	}

	/**
	 * @return Latest recorded interval value (not the interval we are currently tracking)
	 */
	public synchronized IntervalData latest() {
		return last == -1 ? null : intervalData[last];
	}

	/**
	 * Note the objects returned by this iterator must be consumed within DynamicTracker.INTERVAL_SECONDS amount
	 * of time. After this the oldest values will no longer be valid and will be reused; hence, this object
	 * will return no values and instead will throw a ConcurrentModificationException.
	 *
	 * @return The full history we are storing, sorted from latest to oldest
	 */
	public synchronized Iterable<IntervalData> values() {
		return () -> new BufferIterator(last, oldest);
	}

	private class BufferIterator implements Iterator<IntervalData> {
		private int index;
		private final int savedOldest;

		BufferIterator(int index, int savedOldest) {
			this.index = index;
			this.savedOldest = savedOldest;
		}

		@Override
		public boolean hasNext() {
			if(savedOldest != oldest)
				throw new ConcurrentModificationException();

			if(savedOldest == -1)
				return false;

			return index != savedOldest;
		}

		@Override
		public IntervalData next() {
			if(!hasNext()) return null;

			IntervalData retVal = intervalData[index];
			index = (index - 1) % BUFFER_SIZE;
			return retVal;
		}
	}
}