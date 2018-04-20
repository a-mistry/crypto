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

	private static final long TWO_HOUR_NANOS = 3 * 60 * 60 * 1000000000L;
	private static final long SIX_HOUR_NANOS = 6 * 60 * 60 * 1000000000L;
	private static final long SEVEN_HOUR_NANOS = 7 * 60 * 60 * 1000000000L;

	private final double[][] coeffs;

	public Snowbird(MCProperties properties) {
		coeffs = new double[Product.count][8];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			final String coeffsString = properties.getProperty("forecast.snowbird.coeffs." + product);
			log.trace(product + " coeffs " + coeffsString);
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
		int bidTradeCount = 0;
		int askTradeCount = 0;
		int bidCancelCount = 0;
		int askCancelCount = 0;
		int newBidCount = 0;
		int newAskCount = 0;
		double lastTradePrice = latest.midPrice;
		double tradePrice6h = lastTradePrice;
		double btcLastTradePrice = 0.0;
		double btcTradePrice2h = 0.0;
		final long latestTimeNanos = consolidatedHistory.latest().getTimeNanos();
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			ProductSnapshot data = snapshot.getProductSnapshot(product);
			ProductSnapshot btc = snapshot.getProductSnapshot(Product.BTC_USD);

			if(latestTimeNanos - snapshot.getTimeNanos() <= SIX_HOUR_NANOS) {
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
				newBidCount += data.newBidCount;
				newAskCount += data.newAskCount;
			}

			if(latestTimeNanos - snapshot.getTimeNanos() <= SIX_HOUR_NANOS) {
				if(latestTimeNanos == snapshot.getTimeNanos()) {
					// most recent data point
					lastTradePrice = data.lastPrice;
				}
				tradePrice6h = data.lastPrice;
			}

			if(latestTimeNanos - snapshot.getTimeNanos() <= TWO_HOUR_NANOS) {
				if(latestTimeNanos == snapshot.getTimeNanos()) {
					// most recent data point
					btcLastTradePrice = btc.lastPrice;
				}
				btcTradePrice2h = btc.lastPrice;
			}
		}

		final double tradeRatio = (bidTradeCount - askTradeCount) / ((double) book5PctCount);
		final double cancelRatio = (bidCancelCount - askCancelCount) / ((double) book5PctCount);
		final double newRatio = (newBidCount - newAskCount) / ((double) book5PctCount);
		final double ret6h = tradePrice6h == 0 ? 0.0 : lastTradePrice / tradePrice6h - 1.0;
		final double btcRet2h = btcTradePrice2h == 0 ? 0.0 : btcLastTradePrice / btcTradePrice2h - 1.0;

		final double[] productCoeffs = coeffs[product.getIndex()];
		return productCoeffs[0] + productCoeffs[1] * bookRatio + productCoeffs[2] * tradeRatio
			+ productCoeffs[3] * cancelRatio + productCoeffs[4] * cancelRatio * ret6h + productCoeffs[5] * newRatio
			+ productCoeffs[6] * newRatio * ret6h * productCoeffs[7] * btcRet2h;
	}
}
