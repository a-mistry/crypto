package com.mistrycapital.cryptobot.book;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores info on a given price level in the order book and includes references to maintain a doubly linked list
 */
class OrderLine {
	private OrderLine prev;
	private OrderLine next;
	
	private final double price;
	private double size;
	private List<Order> orderList;
	
	OrderLine(double price) {
		this.price = price;
		prev = next = null;
		size = 0.0;
		orderList = new ArrayList<>(10);
	}
	
	public double getSize() {
		return size;
	}
	
	public double getPrice() {
		return price;
	}
	
	public double getCount() {
		return orderList.size();
	}

	public List<Order> getOrders() {
		return orderList;
	}

	/** Calculates size from orders. This should be called after modifying orders at this level */
	private void sumSize() {
		double newSize = 0.0;
		for(int i=0; i<orderList.size(); i++)
			newSize += orderList.get(i).getSize();
		size = newSize;
	}
	
	public void addOrder(final Order order) {
		orderList.add(order);
		sumSize();
	}
	
	public void removeOrder(final Order order) {
		orderList.remove(order);
		sumSize();
		if(orderList.isEmpty()) {
			remove();
		}
	}

	/** Notes that an orders has been modified; used to recalculate level size */
	public void modifiedOrder() {
		sumSize();
	}
	
	/**
	 * Creates new line after the current one with the given price
	 */
	public void insert(final double newPrice) {
		final OrderLine newLine = new OrderLine(newPrice);
		newLine.prev = this;
		newLine.next = next;
		if(next != null) {
			next.prev = newLine;
		}
		next = newLine;
	}
	
	/**
	 * Removes this line from the list
	 */
	public void remove() {
		orderList.clear(); // in case there were any orders
		prev.next = this.next;
		if(this.next != null) {
			this.next.prev = prev;
		}
		this.next = null; // mark for GC
		this.prev = null; // mark for GC
	}
	
	public OrderLine getNext() {
		return next;
	}
}