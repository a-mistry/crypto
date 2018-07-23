package com.mistrycapital.cryptobot.book;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;

/**
 * Subscriber that listens for changes to top of book prices. The order book will fire events when
 * bid/ask prices have changed
 */
public interface TopOfBookSubscriber {
	/**
	 * Called when top of book has changed
	 *
	 * @param product  Product
	 * @param side     Side that changed
	 * @param bidPrice (Potentially new) bid
	 * @param askPrice (Potentiall new) ask
	 */
	void onChanged(Product product, OrderSide side, double bidPrice, double askPrice);
}
