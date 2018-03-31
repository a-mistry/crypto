package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Iterator;

/**
 * Buffer of interval data
 */
public class ProductHistory {
	private static final Logger log = MCLoggerFactory.getLogger();

	/** Length of each interval of data aggregation */
	public static final int INTERVAL_SECONDS = MCProperties.getIntProperty("history.intervalSeconds", 60);
	/** Total amount of history to track (default 1 day) */
	public static final int SECONDS_TO_KEEP = MCProperties.getIntProperty("history.secondsToKeep", 60 * 60 * 24);
	/** Maximum number of intervals to track */
	private static final int MAX_INTERVALS = SECONDS_TO_KEEP / INTERVAL_SECONDS;

	private final Product product;
	private final FileAppender fileAppender;
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

	ProductHistory(Product product, FileAppender fileAppender) {
		this.product = product;
		this.fileAppender = fileAppender;
	}

	public synchronized void add(IntervalData newData) {
		try {
			fileAppender.append(newData.toCSVString());
		} catch(IOException e) {
			log.error("Could not append interval data for " + product, e);
		}
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
		return head != null ? head.data : null;
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