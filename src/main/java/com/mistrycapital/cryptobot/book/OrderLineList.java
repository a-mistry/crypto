package com.mistrycapital.cryptobot.book;

import com.google.common.collect.ImmutableList;

import java.util.List;

class OrderLineList extends OrderLine {
	/** True if the lines are ascending in price */
	private boolean isAscending;

	OrderLineList(boolean isAscending) {
		super(isAscending ? 0.0 : Double.MAX_VALUE);
		this.isAscending = isAscending;
	}

	// This first section has methods for reading the order lines and an iterator class

	/**
	 * @return Price of first order line, or NaN if none
	 */
	public double getFirstPrice() {
		OrderLine first = getNext();
		return first != null ? first.getPrice() : Double.NaN;
	}

	/**
	 * @return Size of first order line, or NaN if none
	 */
	public double getFirstSize() {
		OrderLine first = getNext();
		return first != null ? first.getSize() : Double.NaN;
	}

	/**
	 * @return List of orders in first line
	 */
	public ImmutableList<Order> getFirstOrders() {
		OrderLine first = getNext();
		return first != null ? ImmutableList.copyOf(first.getOrders()) : ImmutableList.of();
	}

	/**
	 * @return Number of orders that appear closer to the midpoint than the given price, inclusive of the price
	 */
	public int getCountBeforePrice(double thresholdPrice) {
		int count = 0;
		for(OrderLine line = this.getNext();
			line != null && withinThreshold(line, thresholdPrice); line = line.getNext()) {
			count += line.getCount();
		}
		return count;
	}

	/**
	 * @return Total size of orders that appear closer to the midpoint than the given price, inclusive of the price
	 */
	public double getSizeBeforePrice(double thresholdPrice) {
		double size = 0.0;
		for(OrderLine line = this.getNext();
			line != null && withinThreshold(line, thresholdPrice); line = line.getNext()) {
			size += line.getSize();
		}
		return size;
	}

	/**
	 * @return true if the given order line has a price before the threshold price, inclusive of the price
	 */
	private boolean withinThreshold(OrderLine line, double thresholdPrice) {
		return (isAscending && line.getPrice() <= thresholdPrice)
			|| (!isAscending && line.getPrice() >= thresholdPrice);
	}

	// This next section has methods for modifying the order lines

	/**
	 * Finds the order line with the given price, or creates one if it doesn't exist. Note that
	 * two lines are considered to be the same line if their prices are within
	 * OrderBook.PRICE_EPSILON of each other
	 *
	 * @param price Price level to find
	 * @return Order line with the given price level
	 */
	public OrderLine findOrCreate(double price) {
		for(OrderLine cur = this; cur != null; cur = cur.getNext()) {
			if(Math.abs(cur.getPrice() - price) < OrderBook.PRICE_EPSILON) {
				// we found the matching price level
				return cur;
			}

			// check if we can insert in the next position
			final OrderLine next = cur.getNext();
			final boolean insertNext = (next == null)
				|| (isAscending && price < next.getPrice() - OrderBook.PRICE_EPSILON)
				|| (!isAscending && price > next.getPrice() + OrderBook.PRICE_EPSILON);
			if(insertNext) {
				cur.insert(price);
				return cur.getNext();
			}
		}
		throw new RuntimeException("Should not reach end of order line list without inserting");
	}

	/**
	 * Clears all order lines from this list
	 */
	public void clear() {
		// help the garbage collector by removing all next references
		OrderLine next;
		while((next = getNext()) != null) {
			next.remove();
		}
	}
}