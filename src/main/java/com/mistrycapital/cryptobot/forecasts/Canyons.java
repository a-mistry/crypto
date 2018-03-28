package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.dynamic.ProductHistory;
import com.mistrycapital.cryptobot.gdax.websocket.Product;

public class Canyons implements Forecast {
	private static final int THREE_HOUR_DATAPOINTS = 3 * 60 * 60 / DynamicTracker.INTERVAL_SECONDS;

	private final DynamicTracker dynamicTracker;
	private final OrderBookManager orderBookManager;

	private static final double alpha = -0.0175;
	private static final double beta1 = 0.0066;
	private static final double beta2 = -0.0040;

	private BBO bbo;
	private Depth[] depths;

	public Canyons(DynamicTracker dynamicTracker, OrderBookManager orderBookManager) {
		this.dynamicTracker = dynamicTracker;
		this.orderBookManager = orderBookManager;
		bbo = new BBO();
		depths = new Depth[1];
		depths[0] = new Depth();
		depths[0].pctFromMid = 0.05;
	}

	@Override
	public double calculate(final Product product) {
		OrderBook orderBook = orderBookManager.getBook(product);
		final int book5PctCount;
		synchronized(this) {
			orderBook.recordDepthsAndBBO(bbo, depths);
			book5PctCount = depths[0].bidCount + depths[0].askCount;
		}

		final ProductHistory history = dynamicTracker.getProductHistory(product);
		int dataPoints = 0;
		int bidTradeCount = 0;
		int askTradeCount = 0;
		for(IntervalData data : history.values()) {
			if(dataPoints >= THREE_HOUR_DATAPOINTS)
				break;
			dataPoints++;

			bidTradeCount += data.bidTradeCount;
			askTradeCount += data.askTradeCount;
		}

		final double bidTradeToBook = ((double) bidTradeCount) / book5PctCount;
		final double askTradeToBook = ((double) askTradeCount) / book5PctCount;
		return alpha + beta1 * bidTradeToBook + beta2 * askTradeToBook;
	}
}
