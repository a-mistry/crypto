package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

public class Solitude implements ForecastCalculator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final int threeHourDatapoints;
	private final double[][] coeffs;

	public Solitude(MCProperties properties) {
		log.info("Using forecast calculator Solitude");
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		threeHourDatapoints = 3 * 60 * 60 / intervalSeconds;

		coeffs = new double[Product.count][3];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			final String coeffsString = properties.getProperty("forecast.solitude.coeffs." + product);
			log.debug(product + " coeffs " + coeffsString);
			if(coeffsString == null)
				throw new RuntimeException("Could not find coeffs for product " + product);
			final String[] split = coeffsString.split(",");
			if(split.length != coeffs[productIndex].length)
				throw new RuntimeException(
					"Wrong number of Solitude coeffs for product " + product + ": " + coeffsString);
			for(int i = 0; i < coeffs[productIndex].length; i++)
				coeffs[productIndex][i] = Double.parseDouble(split[i]);
		}
	}

	@Override
	public double calculate(final ConsolidatedHistory consolidatedHistory, final Product product) {
		final ProductSnapshot latest = consolidatedHistory.latest().getProductSnapshot(product);

		// calc static metrics
		final int book5PctCount = latest.bidCount5Pct + latest.askCount5Pct;
		final double bookRatio = ((double) latest.bidCount5Pct) / book5PctCount;

		// calc dynamic metrics
		int dataPoints = 0;
		int bidTradeCount = 0;
		int askTradeCount = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			if(dataPoints >= threeHourDatapoints)
				break;

			final ProductSnapshot data = snapshot.getProductSnapshot(product);
			bidTradeCount += data.bidTradeCount;
			askTradeCount += data.askTradeCount;

			dataPoints++;
		}

		final double tradeRatio = (bidTradeCount - askTradeCount) / ((double) book5PctCount);

		final double[] productCoeffs = coeffs[product.getIndex()];

		return productCoeffs[0] + productCoeffs[1] * bookRatio + productCoeffs[2] * tradeRatio;
	}
}
