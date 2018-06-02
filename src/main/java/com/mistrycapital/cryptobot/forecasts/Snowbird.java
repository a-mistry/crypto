package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Reversion with modifications forecast
 */
public class Snowbird implements ForecastCalculator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final int twoHourDatapoints;
	private final int sixHourDatapoints;
	private final int twelveHourDatapoints;
	private final double[][] coeffs;

	private static final String[] signalsToUse =
		new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
			"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6"};

	public Snowbird(MCProperties properties) {
		final int intervalSeconds = properties.getIntProperty("history.intervalSeconds");
		twoHourDatapoints = 2 * 60 * 60 / intervalSeconds;
		sixHourDatapoints = twoHourDatapoints * 3;
		twelveHourDatapoints = sixHourDatapoints * 2;

		coeffs = new double[Product.count][signalsToUse.length + 1];
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
		final Map<String,Double> signals = getInputVariables(consolidatedHistory, product);
		final double[] productCoeffs = coeffs[product.getIndex()];

		double fcVal = productCoeffs[0];
		for(int i = 0; i < signalsToUse.length; i++) {
			fcVal += productCoeffs[i + 1] * signals.get(signalsToUse[i]);
		}
		return fcVal;
	}

	@Override
	public Map<String,Double> getInputVariables(final ConsolidatedHistory consolidatedHistory, final Product product) {
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
		int upIntervals = 0;
		int downIntervals = 0;
		int intervalsToMin = 0;
		double minPrice = Double.POSITIVE_INFINITY;
		int intervalsToMax = 0;
		double maxPrice = 0.0;
		double upVolume = 0.0;
		double downVolume = 0.0;
		double sumVolxRet = 0.0;
		double sumVolxRet12 = 0.0;
		double lagRet = 0.0;
		double lagRet6 = 0.0;
		double illiqUp = 0.0;
		double illiqDown = 0.0;
		double sumUpChange = 0.0;
		double sumDownChange = 0.0;
		int clientOidBuyCount = 0;
		int clientOidSellCount = 0;
		int newCount2h = 0;
		int clientOidCount2h = 0;
		double lagBTCRet = 0.0;
		double lagBTCRet6 = 0.0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			ProductSnapshot data = snapshot.getProductSnapshot(product);
			final double periodRet = Double.isNaN(data.ret) ? 0.0 : data.ret;

			final var btcSnapshot = snapshot.getProductSnapshot(Product.BTC_USD);
			final double btcRet = Double.isNaN(btcSnapshot.ret) ? 0.0 : btcSnapshot.ret;

			if(dataPoints < twelveHourDatapoints) {
				sumVolxRet12 += periodRet * data.volume;
			}

			if(dataPoints < sixHourDatapoints) {
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
				newBidCount += data.newBidCount;
				newAskCount += data.newAskCount;
				clientOidBuyCount += data.clientOidBuyCount;
				clientOidSellCount += data.clientOidSellCount;
				final double prevPrice = data.lastPrice / (1 + periodRet);

				if(periodRet >= 0) {
					upIntervals++;
					upVolume += data.volume;
					sumUpChange += data.lastPrice - prevPrice;
				} else {
					downIntervals++;
					downVolume += data.volume;
					sumDownChange += prevPrice - data.lastPrice;
				}
				sumVolxRet += periodRet * data.volume;
				lagRet6 += periodRet;
				illiqUp += periodRet > 0 ? periodRet / data.volume : 0.0;
				illiqDown += periodRet < 0 ? -periodRet / data.volume : 0.0;
				lagBTCRet6 += btcRet;

				if(data.vwap < minPrice) {
					minPrice = data.vwap;
					intervalsToMin = dataPoints + 1;
				}
				if(data.vwap > maxPrice) {
					maxPrice = data.vwap;
					intervalsToMax = dataPoints + 1;
				}
			}

			if(dataPoints < twoHourDatapoints) {
				lagRet += periodRet;
				newCount2h += data.newBidCount + data.newAskCount;
				clientOidCount2h += data.clientOidBuyCount + data.clientOidSellCount;

				lagBTCRet += btcRet;
			}

			dataPoints++;
		}

		final double tradeRatio = ((double) bidTradeCount) / (bidTradeCount + askTradeCount);
		final double cancelRatio = (bidCancelCount - askCancelCount) / ((double) book5PctCount);
		final double newRatio = (newBidCount - newAskCount) / ((double) book5PctCount);
		final double upRatio = ((double) upIntervals) / (upIntervals + downIntervals);
		final double upVolumeRatio = upVolume / (upVolume + downVolume);
		final double informedDirection = ((double) clientOidBuyCount) / (clientOidBuyCount + clientOidSellCount);
		final double informedPct = ((double) clientOidBuyCount + clientOidSellCount) / (newBidCount + newAskCount);
		final double informedPct2h = ((double) clientOidCount2h) / newCount2h;

		return Map.ofEntries(
			entry("lagRet", lagRet),
			entry("lagRet6", lagRet6),
			entry("bookRatio", bookRatio),
			entry("bookRatioxRet", bookRatio * lagRet),
			entry("tradeRatio", tradeRatio),
			entry("tradeRatioxRet", tradeRatio * lagRet),
			entry("cancelRatio", cancelRatio),
			entry("cancelRatioxRet", cancelRatio * lagRet),
			entry("newRatio", newRatio),
			entry("newRatioxRet", newRatio * lagRet),
			entry("upRatio", upRatio),
			entry("upRatioxRet", upRatio * lagRet),
			entry("upVolume", upVolumeRatio),
			entry("upVolumexRet", upVolumeRatio * lagRet),
			entry("sumVolxRet", sumVolxRet),
			entry("diffVolxRet", sumVolxRet12 - sumVolxRet),
			entry("normVolxRet", (sumVolxRet12 - sumVolxRet) / (upVolume + downVolume)), // modified OBV indicator
			entry("timeToMaxMin", (double) intervalsToMax - intervalsToMin),
			entry("illiqUp", illiqUp),
			entry("illiqDown", illiqDown),
			entry("illiqRatio", illiqUp / illiqDown),
			entry("illiqDownxRet", illiqDown * lagRet),
			entry("RSIRatio", sumUpChange / sumDownChange),
			entry("RSIRatioxRet", sumUpChange / sumDownChange * lagRet),
			entry("informedDirxRet", informedDirection * lagRet),
			entry("informedPct", informedPct),
			entry("informedPctxRet", informedPct * lagRet),
			entry("informedPctxIlliq", informedPct * illiqDown),
			entry("informedPctxRSI", informedPct * sumUpChange / sumDownChange * lagRet),
			entry("diffInformed", informedPct - informedPct2h),
			entry("diffInformedxRet", (informedPct - informedPct2h) * lagRet),
			entry("lagBTCRet", lagBTCRet),
			entry("lagBTCRet6", lagBTCRet6)
		);
	}

}
