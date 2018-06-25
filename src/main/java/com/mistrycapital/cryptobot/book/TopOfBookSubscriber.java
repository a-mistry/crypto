package com.mistrycapital.cryptobot.book;

import com.mistrycapital.cryptobot.gdax.common.Product;

/**
 * Subscriber that listens for changes to top of book prices. The order book will fire events when
 * bid/ask prices have changed
 */
public interface TopOfBookSubscriber {
	void onBidChanged(Product product, double bidPrice);

	void onAskChanged(Product product, double askPrice);
}
