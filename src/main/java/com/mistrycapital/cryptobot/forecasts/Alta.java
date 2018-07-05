package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.function.ToDoubleFunction;

import static com.mistrycapital.cryptobot.forecasts.Alta.SignalType.*;

/**
 * Forecast using optimized moving averages and other lag metrics
 */
public class Alta implements ForecastCalculator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final double[][] coeffs;
	private Map<String,Double> variableMap;

	private final int maxLookbackHours;
	private final List<SignalCalculation> signalCalcs;
	private static final String[] signalsToUse =
		new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "tradeRatio10",
			"newRatio10", "weightedMidRetSMA3", "btcRet5", "timeToMaxMin8"};

	public Alta(MCProperties properties) {
		String fcName = getClass().getSimpleName().toLowerCase(Locale.US);
		coeffs = new double[Product.count][signalsToUse.length + 1];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			String coeffsString = properties.getProperty("forecast." + fcName + ".coeffs." + product);
			if(coeffsString == null)
				coeffsString = properties.getProperty("forecast." + fcName + ".coeffs.all");
			if(coeffsString == null)
				throw new RuntimeException("Could not find coeffs for product " + product);
			log.debug(product + " coeffs " + coeffsString);
			final String[] split = coeffsString.split(",");
			if(split.length != coeffs[productIndex].length)
				throw new RuntimeException(
					"Wrong number of coeffs for product " + product + ": " + coeffsString);
			for(int i = 0; i < coeffs[productIndex].length; i++)
			{ coeffs[productIndex][i] = Double.parseDouble(split[i]); }
		}
		variableMap = new HashMap<>();
		for(String signal : signalsToUse) { variableMap.put(signal, 0.0); }

		signalCalcs = new ArrayList<>();
		initSignalCalcs();

		int maxLookbackHours = 0;
		for(SignalCalculation signalCalc : signalCalcs)
			maxLookbackHours = Math.max(maxLookbackHours, signalCalc.lookbackHours);
		// TODO: Figure out why adding 1 here helps
		this.maxLookbackHours = maxLookbackHours + 1;
	}

	/**
	 * This method serves as organizational convenience. All calculations of signals are defined here and in
	 * computeDerivedCalcs(). The rest of the code merely calculates based on these definitions
	 */
	private void initSignalCalcs() {
		// return calcs
		ToDoubleFunction<ProductSnapshot> lastPriceFunction = data -> data.lastPrice;
		signalCalcs.add(new SignalCalculation("lastPrice", 0, STATIC, lastPriceFunction));
		signalCalcs.add(new SignalCalculation("price5h", 5, STATIC, lastPriceFunction));

		// book calcs
		ToDoubleFunction<ProductSnapshot> bookRatioFunction = data ->
			((double) data.bidCount5Pct) / (data.bidCount5Pct + data.askCount5Pct);
		signalCalcs.add(new SignalCalculation("bookRatio", 0, STATIC, bookRatioFunction));
		signalCalcs.add(new SignalCalculation("bookSMA9", 9, SMA, bookRatioFunction));

		// up/down ratio
		signalCalcs.add(new SignalCalculation("upRatio2", 2, SMA, data -> data.ret >= 0 ? 1 : 0));

		// on balance volume calcs
		signalCalcs.add(new SignalCalculation("sumVolxRet3", 3, SUM,
			data -> Math.log(1 + data.ret) * data.volume));
		signalCalcs.add(new SignalCalculation("volume3", 3, SUM, data -> data.volume));

		// RSI
		signalCalcs.add(new SignalCalculation("sumUpChange7", 7, SUM, data ->
			data.ret < 0 ? 0 : data.lastPrice - data.lastPrice / (1 + data.ret)));
		signalCalcs.add(new SignalCalculation("sumDownChange7", 7, SUM, data ->
			data.ret >= 0 ? 0 : data.lastPrice / (1 + data.ret) - data.lastPrice));

		// trade ratio
		signalCalcs.add(new SignalCalculation("bidTradeCount10", 10, SUM, data -> data.bidTradeCount));
		signalCalcs.add(new SignalCalculation("askTradeCount10", 10, SUM, data -> data.askTradeCount));
		signalCalcs.add(new SignalCalculation("book5PctCount", 0, STATIC, data ->
			data.bidCount5Pct + data.askCount5Pct));
		signalCalcs.add(new SignalCalculation("newBidCount10", 10, SUM, data -> data.newBidCount));
		signalCalcs.add(new SignalCalculation("newAskCount10", 10, SUM, data -> data.newAskCount));
		signalCalcs.add(new SignalCalculation("bidCancelCount10", 10, SUM, data -> data.bidCancelCount));
		signalCalcs.add(new SignalCalculation("askCancelCount10", 10, SUM, data -> data.askCancelCount));

		// mid price weighted by top 100 level sizes
		signalCalcs.add(new SignalCalculation("weightedMidLast", 0, STATIC, data -> data.weightedMid100));
		signalCalcs.add(new SignalCalculation("weightedMidSMA3", 3, SMA, data -> data.weightedMid100));
	}

	/**
	 * Computes signals derived from calculations of static and moving average data. This method serves as
	 * organizational convenience - there is no need to modify getInputVariables(); the calculation is
	 * in initSignalCalcs() and here. Things that don't fit can be put in computeExtraCalcs()
	 */
	private void computeDerivedCalcs() {
		variableMap.put("lagRet5", variableMap.get("lastPrice") / variableMap.get("price5h") - 1.0);
		variableMap.put("bookRatioxRet5", variableMap.get("bookRatio") * variableMap.get("lagRet5"));
		variableMap.put("onBalVol3", variableMap.get("sumVolxRet3") / variableMap.get("volume3"));
		if(variableMap.get("sumDownChange7") == 0)
			variableMap.put("RSIRatio7", Double.NaN);
		else
			variableMap.put("RSIRatio7", variableMap.get("sumUpChange7") / variableMap.get("sumDownChange7"));
		variableMap.put("RSIRatio7xRet5", variableMap.get("RSIRatio7") * variableMap.get("lagRet5"));
		variableMap.put("tradeRatio10", variableMap.get("bidTradeCount10") /
			(variableMap.get("bidTradeCount10") + variableMap.get("askTradeCount10")));
		variableMap.put("newRatio10", (variableMap.get("newBidCount10") - variableMap.get("newAskCount10")) /
			variableMap.get("book5PctCount"));
		variableMap.put("cancelRatio10", (variableMap.get("bidCancelCount10") - variableMap.get("askCancelCount10")) /
			variableMap.get("book5PctCount"));

		variableMap.put("weightedMidRetLast", variableMap.get("weightedMidLast") / variableMap.get("lastPrice") - 1);
		variableMap.put("weightedMidRetSMA3", variableMap.get("weightedMidSMA3") / variableMap.get("lastPrice") - 1);
	}

	/**
	 * Calculations that don't fit the framework
	 */
	private void computeExtraCalcs(final ConsolidatedHistory consolidatedHistory, final Product product) {
		// lag BTC return
		final ConsolidatedSnapshot latest = consolidatedHistory.latest();
		final long latestTimeNanos = latest.getTimeNanos();
		final double btcLastPrice = latest.getProductSnapshot(Product.BTC_USD).lastPrice;

		double btcPrice5h = Double.NaN;
		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder((5 + 1) * 3600)) {
			if(consolidatedSnapshot.getTimeNanos() > latestTimeNanos - 5 * 3600000000000L)
				break;
			btcPrice5h = consolidatedSnapshot.getProductSnapshot(Product.BTC_USD).lastPrice;
		}
		variableMap.put("btcRet5", btcPrice5h / btcLastPrice - 1);

		// intervals since max/min
		double minPrice = Double.MAX_VALUE;
		double maxPrice = 0.0;
		int intervalsToMin = 0;
		int intervalsToMax = 0;
		for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder(8 * 3600)) {
			ProductSnapshot snapshot = consolidatedSnapshot.getProductSnapshot(product);

			if(snapshot.vwap < minPrice) {
				minPrice = snapshot.vwap;
				intervalsToMin = 0;
			} else {
				intervalsToMin++;
			}

			if(snapshot.vwap > maxPrice) {
				maxPrice = snapshot.vwap;
				intervalsToMax = 0;
			} else {
				intervalsToMax++;
			}
		}
		variableMap.put("timeToMaxMin8", (double) (intervalsToMax - intervalsToMin));
	}

	@Override
	public double calculate(final ConsolidatedHistory consolidatedHistory, final Product product,
		final ForecastAppender forecastAppender)
	{
		final Map<String,Double> signals = getInputVariables(consolidatedHistory, product);
		final double[] productCoeffs = coeffs[product.getIndex()];

		double fcVal = productCoeffs[0];
		for(int i = 0; i < signalsToUse.length; i++) {
			fcVal += productCoeffs[i + 1] * signals.get(signalsToUse[i]);
		}

		if(forecastAppender != null)
			try {
				forecastAppender.logForecast(product, signals, fcVal);
			} catch(IOException e) {
				log.error("Could not log forecast for " + product, e);
			}

		return fcVal;
	}

	@Override
	public Map<String,Double> getInputVariables(final ConsolidatedHistory consolidatedHistory, final Product product) {
		long latestTimeNanos = consolidatedHistory.latest().getTimeNanos();

		for(SignalCalculation signalCalc : signalCalcs) {
			signalCalc.numDatapoints = 0;
			signalCalc.multiplier = 0.0;
			signalCalc.snapshot = null;
			signalCalc.value = Double.NaN;
		}

		// in first pass, get number of datapoints and snapshots
		for(final ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder(maxLookbackHours * 3600)) {
			final ProductSnapshot snapshot = consolidatedSnapshot.getProductSnapshot(product);

			for(final SignalCalculation signalCalc : signalCalcs) {
				final var timeCompare = Long.compare(
					consolidatedSnapshot.getTimeNanos(),
					latestTimeNanos - signalCalc.lookbackHours * 3600000000000L
				);
				switch(signalCalc.signalType) {
					case STATIC:
						if(timeCompare <= 0)
							signalCalc.snapshot = snapshot;
						break;

					case SMA:
					case EMA:
					case SUM:
						if(timeCompare >= 0)
							signalCalc.numDatapoints++;
						break;
				}
			}
		}

		// calc static signals and multipliers for MAs
		for(final SignalCalculation signalCalc : signalCalcs) {
			switch(signalCalc.signalType) {
				case STATIC:
					signalCalc.value = signalCalc.snapshot == null ? Double.NaN
						: signalCalc.calculate.applyAsDouble(signalCalc.snapshot);
					break;

				case SMA:
					signalCalc.multiplier = 1.0 / signalCalc.numDatapoints;
					break;

				case EMA:
					signalCalc.multiplier = 2.0 / (signalCalc.numDatapoints + 1);
					break;

				case SUM:
					// nothing to do
			}
		}

		// calc MAs
		for(final ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder(maxLookbackHours * 3600)) {
			final ProductSnapshot snapshot = consolidatedSnapshot.getProductSnapshot(product);

			for(final SignalCalculation signalCalc : signalCalcs) {
				if(consolidatedSnapshot.getTimeNanos() < latestTimeNanos - signalCalc.lookbackHours * 3600000000000L)
					continue;

				switch(signalCalc.signalType) {
					case SMA:
						if(Double.isNaN(signalCalc.value)) signalCalc.value = 0.0;
						signalCalc.value += signalCalc.multiplier * signalCalc.calculate.applyAsDouble(snapshot);
						break;

					case EMA:
						final var curValue = signalCalc.calculate.applyAsDouble(snapshot);
						if(Double.isNaN(signalCalc.value)) signalCalc.value = curValue;
						signalCalc.value =
							(1 - signalCalc.multiplier) * signalCalc.value + signalCalc.multiplier * curValue;
						break;

					case SUM:
						if(Double.isNaN(signalCalc.value)) signalCalc.value = 0.0;
						signalCalc.value += signalCalc.calculate.applyAsDouble(snapshot);

					case STATIC:
						// nothing to do here
				}
			}
		}

		// put in map
		for(final SignalCalculation signalCalc : signalCalcs) {
			variableMap.put(signalCalc.name, signalCalc.value);
		}

		// compute any signals derived from earlier calcs
		computeDerivedCalcs();

		// other calcs that don't fit the framework
		computeExtraCalcs(consolidatedHistory, product);

		return variableMap;
	}

	enum SignalType {
		/** Point in time signal */
		STATIC,
		/** Simple moving average */
		SMA,
		/** Exponential moving average */
		EMA,
		/** Moving sum */
		SUM
	}

	class SignalCalculation {
		/** Name of this variable */
		String name;
		/** Hours to go back (as of for point-in-time, whole period for EMA) */
		int lookbackHours;
		/** Calculate a single point, moving average, or exponential moving average */
		SignalType signalType;
		/** Used to get the calculation from the snapshot */
		ToDoubleFunction<ProductSnapshot> calculate;

		/** Number of datapoints - used in MA calcs */
		int numDatapoints;
		/** Moving average multiplier */
		double multiplier;
		/** Snapshot as of correct time - used in static calcs */
		ProductSnapshot snapshot;
		/** Value calculated */
		double value;

		SignalCalculation(String name, int lookbackHours, SignalType signalType,
			ToDoubleFunction<ProductSnapshot> calculate)
		{
			this.name = name;
			this.lookbackHours = lookbackHours;
			this.signalType = signalType;
			this.calculate = calculate;
		}
	}
}
