package com.mistrycapital.cryptobot.book;

/**
 * Stores info on a given price level in the order book and includes references to maintain a doubly linked list
 */
class OrderLine {
	private OrderLine prev;
	private OrderLine next;
	
	private final double price;
	private double size;
	private int count;
	
	OrderLine(double price) {
		this.price = price;
		prev = next = null;
		size = 0.0;
		count = 0;
	}
	
	public double getSize() {
		return size;
	}
	
	public double getPrice() {
		return price;
	}
	
	public double getCount() {
		return count;
	}
	
	public void addOrder(final double orderSize) {
		size += orderSize;
		count++;
	}
	
	public void removeOrder(final double orderSize) {
		size -= orderSize;
		count--;
		if(count <= 0) {
			remove();
		}
	}
	
	public void modifyOrder(final double oldSize, final double newSize) {
		size += newSize - oldSize;
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