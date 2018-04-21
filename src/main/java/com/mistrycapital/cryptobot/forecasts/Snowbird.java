package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

public class Snowbird implements ForecastCalculator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final int twoHourDatapoints;
	private final int sixHourDatapoints;
	private final double[][] coeffs;

	public Snowbird(MCProperties properties) {
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		twoHourDatapoints = 2 * 60 * 60 / intervalSeconds;
		sixHourDatapoints = twoHourDatapoints * 3;

		coeffs = new double[Product.count][7];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			final String coeffsString = properties.getProperty("forecast.snowbird.coeffs." + product);
			log.debug(product + " coeffs " + coeffsString);
			if(coeffsString == null)
				throw new RuntimeException("Could not find coeffs for product " + product);
			final String[] split = coeffsString.split(",");
			if(split.length != coeffs[productIndex].length)
				throw new RuntimeException(
					"Wrong number of Snowbird coeffs for product " + product + ": " + coeffsString);
			for(int i = 0; i < coeffs[productIndex].length; i++)
				coeffs[productIndex][i] = Double.parseDouble(split[i]);
		}
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
		int newBidCount = 0;
		int newAskCount = 0;
		double lagRet = 0.0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			ProductSnapshot data = snapshot.getProductSnapshot(product);
			if(dataPoints < sixHourDatapoints) {
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
				newBidCount += data.newBidCount;
				newAskCount += data.newAskCount;
			}

			if(dataPoints < twoHourDatapoints) {
				lagRet += data.ret;
			}

			dataPoints++;
		}

		final double tradeRatio = (bidTradeCount - askTradeCount) / ((double) book5PctCount);
		final double cancelRatio = (bidCancelCount - askCancelCount) / ((double) book5PctCount);
		final double newRatio = (newBidCount - newAskCount) / ((double) book5PctCount);

		final double[] productCoeffs = coeffs[product.getIndex()];
		return productCoeffs[0] + productCoeffs[1] * bookRatio + productCoeffs[2] * tradeRatio
			+ productCoeffs[3] * cancelRatio + productCoeffs[4] * cancelRatio * lagRet + productCoeffs[5] * newRatio
			+ productCoeffs[6] * newRatio * lagRet;
	}
}
