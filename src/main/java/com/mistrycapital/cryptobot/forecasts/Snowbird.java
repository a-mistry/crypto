package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.PeriodicEvaluator;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCProperties;

public class Snowbird implements ForecastCalculator {
	private final int threeHourDatapoints;
	private final int sixHourDatapoints;
	private final double[] coeffs;

	public Snowbird(MCProperties properties) {
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		threeHourDatapoints = 3 * 60 * 60 / intervalSeconds;
		sixHourDatapoints = threeHourDatapoints * 2;

		final String coeffsString = properties.getProperty("forecast.snowbird.coeffs");
		final String[] split = coeffsString.split(",");
		coeffs = new double[8];
		if(split.length != coeffs.length)
			throw new RuntimeException("Wrong number of snowbird coeffs: " + coeffsString);
		for(int i=0; i<coeffs.length; i++)
			coeffs[i] = Double.parseDouble(split[i]);
	}

	@Override
	public double calculate(final ConsolidatedHistory consolidatedHistory, final Product product) {
		ProductSnapshot latest = consolidatedHistory.latest().getProductSnapshot(product);

		// calc static metrics
		final int book5PctCount = latest.bidCount5Pct + latest.askCount5Pct;
		final double bookRatio = ((double) latest.bidCount5Pct) / book5PctCount;

		// calc dynamic metrics
		int dataPoints = 0;
		int bidTradeCount = 0;
		int askTradeCount = 0;
		int bidCancelCount = 0;
		int askCancelCount = 0;
		double lastTradePrice = latest.midPrice;
		double tradePrice6h = lastTradePrice;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			ProductSnapshot data = snapshot.getProductSnapshot(product);
			if(dataPoints < threeHourDatapoints) {
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
			}

			if(dataPoints < sixHourDatapoints) {
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
