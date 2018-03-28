package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.Product;

/**
 * Uses book depth
 */
public class Hunter implements Forecast {
	private final OrderBookManager orderBookManager;

	private static final double alpha = -0.0057;
	private static final double beta = -0.0114;

	private BBO bbo;
	private Depth[] depths;

	public Hunter(OrderBookManager orderBookManager) {
		this.orderBookManager = orderBookManager;
		bbo = new BBO();
		depths = new Depth[1];
		depths[0] = new Depth();
		depths[0].pctFromMid = 0.05;
	}

	@Override
	public double calculate(final Product product) {
		OrderBook orderBook = orderBookManager.getBook(product);
		final double ratio;
		synchronized(this) {
			orderBook.recordDepthsAndBBO(bbo, depths);
			final int bidCount = depths[0].bidCount;
			final int askCount = depths[0].askCount;
			ratio = ((double) bidCount) / (bidCount + askCount);
		}
		return alpha + beta*ratio;
	}
}