package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.PeriodicEvaluator;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.dynamic.ProductHistory;
import com.mistrycapital.cryptobot.gdax.websocket.Product;

public class Snowbird implements ForecastCalculator {
	private static final int THREE_HOUR_DATAPOINTS = 3 * 60 * 60 / PeriodicEvaluator.INTERVAL_SECONDS;
	private static final int SIX_HOUR_DATAPOINTS = THREE_HOUR_DATAPOINTS * 2;

	private static final double[] coeffs = new double[] {
		-0.000550307499740531,
		-0.0010944365777197412,
		-0.001180854132159011,
		0.0012972390272206194,
		5.45821367886466e-06,
		-8.92223968701952e-06,
		0.00015720230759043175,
		-0.00024395633450024047
	};

	public Snowbird() {
	}

	@Override
	public double calculate(final ConsolidatedData consolidatedData, final Product product) {
		// calc static metrics
		final Depth depth5Pct = consolidatedData.getDepth5Pct(product);
		final int bid5PctCount = depth5Pct.bidCount;
		final int ask5PctCount = depth5Pct.askCount;
		final int book5PctCount = bid5PctCount + ask5PctCount;
		final double bookRatio = ((double) bid5PctCount) / book5PctCount;

		// calc dynamic metrics
		final ProductHistory history = consolidatedData.getHistory(product);
		int dataPoints = 0;
		int bidTradeCount = 0;
		int askTradeCount = 0;
		int bidCancelCount = 0;
		int askCancelCount = 0;
		double lastTradePrice = consolidatedData.getBBO(product).midPrice();
		double tradePrice6h = lastTradePrice;
		for(IntervalData data : history.values()) {
			if(dataPoints < THREE_HOUR_DATAPOINTS) {
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
			}

			if(dataPoints < SIX_HOUR_DATAPOINTS) {
				if(dataPoints == 0) {
					lastTradePrice = data.lastPrice;
				}
				tradePrice6h = data.lastPrice;
			}

			dataPoints++;
		}

		final double bidTradeToBook = ((double) bidTradeCount) / book5PctCount;
		final double askTradeToBook = ((double) askTradeCount) / book5PctCount;
		final double bidCancelToBook = ((double) bidCancelCount) / book5PctCount;
		final double askCancelToBook = ((double) askCancelCount) / book5PctCount;
		final double ret6h = tradePrice6h == 0 ? 0.0 : lastTradePrice / tradePrice6h - 1.0;

		return coeffs[0] + coeffs[1] * bookRatio + coeffs[2] * bidTradeToBook + coeffs[3] * askTradeToBook
			+ coeffs[4] * bidCancelToBook + coeffs[5] * askCancelToBook
			+ coeffs[6] * bidCancelToBook * ret6h + coeffs[7] * askCancelToBook * ret6h;
	}
}
