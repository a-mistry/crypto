package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.time.Intervalizer;

import java.util.ConcurrentModificationException;
import java.util.Iterator;

/**
 * History of static and dynamic data for all products
 */
public class ConsolidatedHistory {
	/** Maximum number of intervals to track */
	private final int maxIntervals;

	/** Recorded snapshot data, in a linked list with the head containing the latest value */
	private ConsolidatedSnapshot[] buffer;
	/** Oldest node */
	private int head;
	/** Newest node */
	private int tail;
	/** Used to track if this object was modified during iteration */
	private boolean modified;

	public ConsolidatedHistory(Intervalizer intervalizer) {
		maxIntervals = intervalizer.getHistoryIntervals();
		buffer = new ConsolidatedSnapshot[maxIntervals];
	}

	public synchronized void add(ConsolidatedSnapshot newData) {
		modified = true;

		if(tail != 0 || buffer[tail] != null) {
			tail = (tail + 1) % maxIntervals;
			if(head == tail)
				head = (head + 1) % maxIntervals;
		}
		buffer[tail] = newData;
	}

	/**
	 * @return Latest recorded interval value (not the interval we are currently tracking)
	 */
	public synchronized ConsolidatedSnapshot latest() {
		return buffer[tail];
	}

	/**
	 * @return The full history we are storing, sorted from oldest to latest
	 */
	public synchronized Iterable<ConsolidatedSnapshot> values() {
		modified = false;
		return () -> new BufferIterator(this);
	}

	private class BufferIterator implements Iterator<ConsolidatedSnapshot> {
		private final ConsolidatedHistory history;
		private int cur;

		BufferIterator(ConsolidatedHistory history) {
			this.history = history;
			cur = tail;
		}

		@Override
		public boolean hasNext() {
			synchronized(history) {
				if(modified) throw new ConcurrentModificationException();
				return cur >= 0 && buffer[cur] != null;
			}
		}

		@Override
		public ConsolidatedSnapshot next() {
			synchronized(history) {
				if(modified) throw new ConcurrentModificationException();
				if(cur < 0 || buffer[cur] == null) {
					return null;
				} else {
					ConsolidatedSnapshot retVal = buffer[cur];
					if(cur == head)
						cur = -1;
					else
						cur = (cur - 1 + maxIntervals) % maxIntervals;
					return retVal;
				}
			}
		}
	}
}
