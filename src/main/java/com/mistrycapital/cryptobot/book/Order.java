package com.mistrycapital.cryptobot.book;

import java.util.UUID;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;

public class Order {
	private UUID id;
	private double price;
	private double size;
	private long timeMicros;
	private OrderSide side;
	private OrderLine orderLine;
	
	void reset(final UUID id, final double price, final double size, final long timeMicros, final OrderSide side) {
		this.id = id;
		this.price = price;
		this.size = size;
		this.timeMicros = timeMicros;
		this.side = side;
		this.orderLine = null;
	}
	
	void setLine(final OrderLine orderLine) {
		this.orderLine = orderLine;
		orderLine.addOrder(size);
	}
	
	void changeSize(final double newSize) {
		if(orderLine != null) {
			orderLine.modifyOrder(size, newSize);
		}
		this.size = newSize;
	}
	
	void destroy() {
		if(orderLine != null) {
			orderLine.removeOrder(size);
		}
		id = null;
		orderLine = null;
	}
	
	public UUID getId() {
		return id;
	}
	
	public double getPrice() {
		return price;
	}
	
	public double getSize() {
		return size;
	}
	
	public long getTimeMicros() {
		return timeMicros;
	}
	
	public OrderSide getSide() {
		return side;
	}
	
	public OrderLine getOrderLine() {
		return orderLine;
	}
}