package com.mistrycapital.cryptobot.regression;

import com.mistrycapital.cryptobot.gdax.common.Product;

import java.io.Serializable;

public class TimeProduct implements Comparable<TimeProduct>, Serializable {
	public final long timeInNanos;
	public final Product product;

	TimeProduct(long timeInNanos, Product product) {
		this.timeInNanos = timeInNanos;
		this.product = product;
	}

	@Override
	public int compareTo(final TimeProduct b) {
		int timeCompare = Long.compare(timeInNanos, b.timeInNanos);
		return timeCompare != 0 ? timeCompare : product.compareTo(b.product);
	}
}
