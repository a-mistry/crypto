package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.websocket.Product;

import java.util.Iterator;

/**
 * Buffer of interval data
 */
public class ProductHistory {
	private static final int MAX_INTERVALS = DynamicTracker.SECONDS_TO_KEEP / DynamicTracker.INTERVAL_SECONDS;

	private final Product product;
	/** Recorded data for each interval, in a linked list with the head containing the latest value */
	private IntervalDataListNode head;

	private class IntervalDataListNode {
		IntervalData data;
		IntervalDataListNode prev;

		IntervalDataListNode(IntervalData data, IntervalDataListNode prev) {
			this.data = data;
			this.prev = prev;
		}
	}

	ProductHistory(Product product) {
		this.product = product;
	}

	public synchronized void add(IntervalData newData) {
		// TO DO: append data to file
		if(head == null) {
			head = new IntervalDataListNode(newData, null);
			return;
		}

		head = new IntervalDataListNode(newData, head);

		// remove oldest if needed
		IntervalDataListNode cur = head;
		int count = 1;
		while(count < MAX_INTERVALS && cur.prev != null) {
			cur = cur.prev;
			count++;
		}
		if(count == MAX_INTERVALS)
			cur.prev = null;
	}

	/**
	 * @return Latest recorded interval value (not the interval we are currently tracking)
	 */
	public synchronized IntervalData latest() {
		return head.data;
	}

	/**
	 * @return The full history we are storing, sorted from oldest to latest
	 */
	public synchronized Iterable<IntervalData> values() {
		final ProductHistory history = this;
		return () -> new BufferIterator(history, head);
	}

	private class BufferIterator implements Iterator<IntervalData> {
		private final ProductHistory history;
		private IntervalDataListNode cur;

		BufferIterator(ProductHistory history, IntervalDataListNode latest) {
			this.history = history;
			cur = latest;
		}

		@Override
		public boolean hasNext() {
			synchronized(history) {
				return cur != null;
			}
		}

		@Override
		public IntervalData next() {
			synchronized(history) {
				if(cur == null) {
					return null;
				} else {
					IntervalData retVal = cur.data;
					cur = cur.prev;
					return retVal;
				}
			}
		}
	}
}