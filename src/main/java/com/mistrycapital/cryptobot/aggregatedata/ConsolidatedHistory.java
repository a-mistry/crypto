package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.time.Intervalizer;

import java.util.Iterator;

/**
 * History of static and dynamic data for all products
 */
public class ConsolidatedHistory {
	/** Maximum number of intervals to track */
	private final int maxIntervals;

	/** Recorded snapshot data, in a linked list with the head containing the latest value */
	private ConsolidatedDataListNode head;

	private class ConsolidatedDataListNode {
		ConsolidatedSnapshot data;
		ConsolidatedDataListNode prev;

		ConsolidatedDataListNode(ConsolidatedSnapshot data, ConsolidatedDataListNode prev) {
			this.data = data;
			this.prev = prev;
		}
	}

	public ConsolidatedHistory(Intervalizer intervalizer) {
		maxIntervals = intervalizer.getHistoryIntervals();
	}

	public synchronized void add(ConsolidatedSnapshot newData) {
		if(head == null) {
			head = new ConsolidatedDataListNode(newData, null);
			return;
		}

		head = new ConsolidatedDataListNode(newData, head);

		// remove oldest if needed
		ConsolidatedDataListNode cur = head;
		int count = 1;
		while(count < maxIntervals && cur.prev != null) {
			cur = cur.prev;
			count++;
		}
		if(count == maxIntervals)
			cur.prev = null;
	}

	/**
	 * @return Latest recorded interval value (not the interval we are currently tracking)
	 */
	public synchronized ConsolidatedSnapshot latest() {
		return head != null ? head.data : null;
	}

	/**
	 * @return The full history we are storing, sorted from oldest to latest
	 */
	public synchronized Iterable<ConsolidatedSnapshot> values() {
		final ConsolidatedHistory history = this;
		return () -> new BufferIterator(history, head);
	}

	private class BufferIterator implements Iterator<ConsolidatedSnapshot> {
		private final ConsolidatedHistory history;
		private ConsolidatedDataListNode cur;

		BufferIterator(ConsolidatedHistory history, ConsolidatedDataListNode latest) {
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
		public ConsolidatedSnapshot next() {
			synchronized(history) {
				if(cur == null) {
					return null;
				} else {
					ConsolidatedSnapshot retVal = cur.data;
					cur = cur.prev;
					return retVal;
				}
			}
		}
	}
}
