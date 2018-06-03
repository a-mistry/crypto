package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.HashMap;
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
	private Map<String,Double> variableMap;

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
		variableMap = new HashMap<>();
		for(String signal : signalsToUse)
			variableMap.put(signal, 0.0);
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
		double volume = 0.0;
		double sumVolxRet = 0.0;
		double sumVolxRet12 = 0.0;
		double lagRet = 0.0;
		double lagRet6 = 0.0;
		double sumUpChange = 0.0;
		double sumDownChange = 0.0;
		double lagBTCRet6 = 0.0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			dataPoints++;

			if(dataPoints > twelveHourDatapoints)
				break;

			ProductSnapshot data = snapshot.getProductSnapshot(product);
			final double periodRet = Double.isNaN(data.ret) ? 0.0 : Math.log(1 + data.ret);

			sumVolxRet12 += periodRet * data.volume;

			if(dataPoints > sixHourDatapoints)
				continue;

			final var btcSnapshot = snapshot.getProductSnapshot(Product.BTC_USD);
			final double btcRet = Double.isNaN(btcSnapshot.ret) ? 0.0 : Math.log(1 + btcSnapshot.ret);

			bidTradeCount += data.bidTradeCount;
			askTradeCount += data.askTradeCount;
			bidCancelCount += data.bidCancelCount;
			askCancelCount += data.askCancelCount;
			newBidCount += data.newBidCount;
			newAskCount += data.newAskCount;
			final double prevPrice = data.lastPrice / (1 + periodRet);

			if(periodRet >= 0) {
				upIntervals++;
				sumUpChange += data.lastPrice - prevPrice;
			} else {
				downIntervals++;
				sumDownChange += prevPrice - data.lastPrice;
			}
			volume += data.volume;
			sumVolxRet += periodRet * data.volume;
			lagRet6 += periodRet;
			lagBTCRet6 += btcRet;

			if(data.vwap < minPrice) {
				minPrice = data.vwap;
				intervalsToMin = dataPoints + 1;
			}
			if(data.vwap > maxPrice) {
				maxPrice = data.vwap;
				intervalsToMax = dataPoints + 1;
			}

			if(dataPoints > twoHourDatapoints)
				continue;

			lagRet += periodRet;

		}

		final double tradeRatio = ((double) bidTradeCount) / (bidTradeCount + askTradeCount);
		final double cancelRatio = (bidCancelCount - askCancelCount) / ((double) book5PctCount);
		final double newRatio = (newBidCount - newAskCount) / ((double) book5PctCount);
		final double upRatio = ((double) upIntervals) / (upIntervals + downIntervals);

		// Things we tried that didn't work
		// o 2 hour lag return, btc return
		// o up volume ratio (up volume / volume), also x ret
		// o un-normalized on balance volume (sum of volume x ret)
		// o illiq up and down (ret / volume when down) - down was better, also x ret
		// o RSI ratio without lag ret
		variableMap.put("lagRet6", lagRet6);
		variableMap.put("bookRatioxRet", bookRatio * lagRet);
		variableMap.put("tradeRatio", tradeRatio);
		variableMap.put("cancelRatio", cancelRatio);
		variableMap.put("newRatio", newRatio);
		variableMap.put("upRatioxRet", upRatio * lagRet);
		variableMap.put("normVolxRet", (sumVolxRet12 - sumVolxRet) / volume); // modified OBV indicator
		variableMap.put("timeToMaxMin", (double) intervalsToMax - intervalsToMin);
		variableMap.put("RSIRatioxRet", sumUpChange / sumDownChange * lagRet);
		variableMap.put("lagBTCRet6", lagBTCRet6);

		// These don't contribute much (0.1% R^2, low t-stats) but may be justified
//		variableMap.put("bookRatio", bookRatio);
//		variableMap.put("tradeRatioxRet", tradeRatio * lagRet);
//		variableMap.put("cancelRatioxRet", cancelRatio * lagRet);
//		variableMap.put("newRatioxRet", newRatio * lagRet);
//		variableMap.put("upRatio", upRatio);

		return variableMap;
	}

}
