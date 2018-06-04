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

	private static final int MIN_DATA_POINTS = 10;

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

		// get 2h, 6h lag returns
		double price2h = Double.NaN;
		double price6h = Double.NaN;
		double priceBTC6h = Double.NaN;
		int retDataPoints = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values())
			if(retDataPoints <= sixHourDatapoints) {
				retDataPoints++;
				ProductSnapshot data = snapshot.getProductSnapshot(product);
				if(Double.isNaN(data.lastPrice))
					continue;

				price6h = data.lastPrice;
				final double priceBTC = snapshot.getProductSnapshot(Product.BTC_USD).lastPrice;
				if(!Double.isNaN(priceBTC))
					priceBTC6h = priceBTC;

				if(retDataPoints <= twoHourDatapoints) {
					price2h = data.lastPrice;
				}
			} else break;
		final double lagRet = retDataPoints < MIN_DATA_POINTS ? Double.NaN : Math.log(latest.lastPrice / price2h);
		final double lagRet6 = retDataPoints < MIN_DATA_POINTS ? Double.NaN : Math.log(latest.lastPrice / price6h);
		final double lagBTCRet6 = retDataPoints < MIN_DATA_POINTS ? Double.NaN : Math.log(
			consolidatedHistory.latest().getProductSnapshot(Product.BTC_USD).lastPrice / priceBTC6h
		);

		// calc on balance volume metrics
		double sumVolxRet = 0.0;
		double sumVolxRet12 = 0.0;
		double volume = 0.0;
		int volDataPoints = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values())
			if(volDataPoints <= twelveHourDatapoints) {
				volDataPoints++;
				ProductSnapshot data = snapshot.getProductSnapshot(product);

				if(Double.isNaN(data.ret) || Double.isNaN(data.volume))
					continue;

				final double volxRet = Math.log(1 + data.ret) * data.volume;
				sumVolxRet12 += volxRet;
				if(volDataPoints <= sixHourDatapoints) {
					volume += data.volume;
					sumVolxRet += volxRet;
				}
			} else break;
		if(volDataPoints < MIN_DATA_POINTS) {
			sumVolxRet = sumVolxRet12 = volume = Double.NaN;
		}

		// calc 6h sum metrics
		int bidTradeCount = 0;
		int askTradeCount = 0;
		int bidCancelCount = 0;
		int askCancelCount = 0;
		int newBidCount = 0;
		int newAskCount = 0;
		int sumDataPoints = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values())
			if(sumDataPoints <= sixHourDatapoints) {
				sumDataPoints++;

				ProductSnapshot data = snapshot.getProductSnapshot(product);
				// these are longs so they cannot be NaN
				bidTradeCount += data.bidTradeCount;
				askTradeCount += data.askTradeCount;
				bidCancelCount += data.bidCancelCount;
				askCancelCount += data.askCancelCount;
				newBidCount += data.newBidCount;
				newAskCount += data.newAskCount;
			} else break;
		final double tradeRatio;
		final double cancelRatio;
		final double newRatio;
		if(sumDataPoints < MIN_DATA_POINTS) {
			tradeRatio = cancelRatio = newRatio = Double.NaN;
		} else {
			tradeRatio = ((double) bidTradeCount) / (bidTradeCount + askTradeCount);
			cancelRatio = (bidCancelCount - askCancelCount) / ((double) book5PctCount);
			newRatio = (newBidCount - newAskCount) / ((double) book5PctCount);
		}

		// calc price change interval metrics
		int upIntervals = 0;
		int downIntervals = 0;
		int intervalsToMin = 0;
		int intervalsToMax = 0;
		double minPrice = Double.POSITIVE_INFINITY;
		double maxPrice = 0.0;
		double sumUpChange = 0.0;
		double sumDownChange = 0.0;
		int changeDataPoints = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values())
			if(changeDataPoints <= sixHourDatapoints) {
				changeDataPoints++;

				ProductSnapshot data = snapshot.getProductSnapshot(product);
				if(Double.isNaN(data.ret) || Double.isNaN(data.lastPrice) || Double.isNaN(data.vwap))
					continue;

				final double prevPrice = data.lastPrice / (1 + data.ret);
				if(data.ret >= 0) {
					upIntervals++;
					sumUpChange += data.lastPrice - prevPrice;
				} else {
					downIntervals++;
					sumDownChange += prevPrice - data.lastPrice;
				}

				if(data.vwap < minPrice) {
					minPrice = data.vwap;
					intervalsToMin = changeDataPoints + 1;
				}
				if(data.vwap > maxPrice) {
					maxPrice = data.vwap;
					intervalsToMax = changeDataPoints + 1;
				}
			} else break;
		final double upRatio;
		final double timeToMaxMin;
		final double RSIRatio;
		if(changeDataPoints < MIN_DATA_POINTS) {
			upRatio = timeToMaxMin = RSIRatio = Double.NaN;
		} else {
			upRatio = ((double) upIntervals) / (upIntervals + downIntervals);
			timeToMaxMin = intervalsToMax - intervalsToMin;
			RSIRatio = sumUpChange / sumDownChange;
		}

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
		variableMap.put("timeToMaxMin", timeToMaxMin);
		variableMap.put("RSIRatioxRet", RSIRatio * lagRet);
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
