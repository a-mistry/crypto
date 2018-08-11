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
import java.util.function.IntFunction;
import java.util.function.ToDoubleFunction;

import static com.mistrycapital.cryptobot.forecasts.Alta.SignalType.*;

/**
 * Forecast using optimized moving averages and other lag metrics
 */
public class Alta implements ForecastCalculator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final double[][] coeffs;
	private Map<String,Double> variableMap;

	private final boolean calcAllSignals;
	private final int maxLookbackHours;
	private final List<SignalCalculation> signalCalcs;
	public static final String[] signalsToUse =
		new String[] {"bookSMA9", "onBalVol2", "RSIRatioxRet10", "btcRet2", "bookRatioxRet1", "newRatio9",
			"cancelRatio8", "tradeRatio12", "upRatio3", "RSIRatio3", "timeToMaxMin10", "lagRet5",
			"weightedMidRetLast"};
	public static final Set<String> signalsToUseSet = Set.of(signalsToUse);

	public Alta(MCProperties properties) {
		String fcName = getClass().getSimpleName().toLowerCase(Locale.US);
		calcAllSignals = properties.getBooleanProperty("forecast." + fcName + ".calcAllSignals", false);
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
	 * Adds the given signal at the lookback needed if the final variable is used. For example,
	 * addSignalCalc("tradeRatio", t -> new SignalCalculation(...)) will check if any of tradeRatio1, tradeRatio2, etc
	 * are in the signalsToUse set and if so, it will construct the SignalCalculation at the same lookback
	 * and add it if it is not already in signalCalcs. Note that multiple additions of the same named signal
	 * will be ignored in favor of the first.
	 */
	private void addSignalCalc(String finalVarName, IntFunction<SignalCalculation> generateSignalCalcForLookback) {
		for(int t = 1; t <= 12; t++)
			if(calcAllSignals || signalsToUseSet.contains(finalVarName + t)) {
				SignalCalculation signalCalc = generateSignalCalcForLookback.apply(t);
				final boolean exists = signalCalcs.stream().anyMatch(x -> x.name.equals(signalCalc.name));
				if(!exists) {
					signalCalcs.add(signalCalc);
				}
			}
	}

	private void putDerivedCalc(String finalVarName, String thisVarName, IntFunction<Double> generateDerivedCalc) {
		for(int t = 1; t <= 12; t++)
			if(calcAllSignals || signalsToUseSet.contains(finalVarName + t))
				variableMap.put(thisVarName + t, generateDerivedCalc.apply(t));
	}

	/**
	 * This method serves as organizational convenience. All calculations of signals are defined here and in
	 * computeDerivedCalcs(). The rest of the code merely calculates based on these definitions
	 */
	private void initSignalCalcs() {
		// return calcs
		ToDoubleFunction<ProductSnapshot> lastPriceFunction = data -> data.lastPrice;
		addSignalCalc("lagRet", t -> new SignalCalculation("lastPrice", 0, STATIC, lastPriceFunction));
		IntFunction<SignalCalculation> lagPriceGenerator = t ->
			new SignalCalculation("price" + t + "h", t, STATIC, lastPriceFunction);
		addSignalCalc("lagRet", lagPriceGenerator);

		// book calcs
		ToDoubleFunction<ProductSnapshot> bookRatioFunction = data ->
			((double) data.bidCount5Pct) / (data.bidCount5Pct + data.askCount5Pct);
		IntFunction<SignalCalculation> bookRatioGenerator = t ->
			new SignalCalculation("bookRatio", 0, STATIC, bookRatioFunction);
		IntFunction<SignalCalculation> book5PctGenerator = t ->
			new SignalCalculation("book5PctCount", 0, STATIC, data -> data.bidCount5Pct + data.askCount5Pct);
		addSignalCalc("bookRatio", bookRatioGenerator);
		addSignalCalc("bookRatioxRet", lagPriceGenerator);
		addSignalCalc("bookRatioxRet", bookRatioGenerator);
		addSignalCalc("bookRatio", book5PctGenerator);
		addSignalCalc("bookRatioxRet", book5PctGenerator);
		addSignalCalc("bookSMA", t -> new SignalCalculation("bookSMA" + t, t, SMA, bookRatioFunction));

		// last weighted mid
		signalCalcs.add(new SignalCalculation("weightedMidLast", 0, STATIC, data -> data.weightedMid100));

		// up/down ratio
		addSignalCalc("upRatio", t -> new SignalCalculation("upRatio" + t, t, SMA, data -> data.ret >= 0 ? 1 : 0));

		// on balance volume calcs
		addSignalCalc("onBalVol", t ->
			new SignalCalculation("sumVolxRet" + t, t, SUM, data -> Math.log(1 + data.ret) * data.volume));
		addSignalCalc("onBalVol", t ->
			new SignalCalculation("volume" + t, t, SUM, data -> data.volume));

		// RSI
		IntFunction<SignalCalculation> sumUpGenerator = t ->
			new SignalCalculation("sumUpChange" + t, t, SUM, data ->
				data.ret < 0 ? 0 : data.lastPrice - data.lastPrice / (1 + data.ret));
		IntFunction<SignalCalculation> sumDownGenerator = t ->
			new SignalCalculation("sumDownChange" + t, t, SUM, data ->
				data.ret >= 0 ? 0 : data.lastPrice / (1 + data.ret) - data.lastPrice);
		addSignalCalc("RSIRatio", sumUpGenerator);
		addSignalCalc("RSIRatioxRet", lagPriceGenerator);
		addSignalCalc("RSIRatioxRet", sumUpGenerator);
		addSignalCalc("RSIRatio", sumDownGenerator);
		addSignalCalc("RSIRatioxRet", sumDownGenerator);

		// trade ratio
		addSignalCalc("tradeRatio", t ->
			new SignalCalculation("bidTradeCount" + t, t, SUM, data -> data.bidTradeCount));
		addSignalCalc("tradeRatio", t ->
			new SignalCalculation("askTradeCount" + t, t, SUM, data -> data.askTradeCount));
		addSignalCalc("newRatio", t ->
			new SignalCalculation("newBidCount" + t, t, SUM, data -> data.newBidCount));
		addSignalCalc("newRatio", t ->
			new SignalCalculation("newAskCount" + t, t, SUM, data -> data.newAskCount));
		addSignalCalc("cancelRatio", t ->
			new SignalCalculation("bidCancelCount" + t, t, SUM, data -> data.bidCancelCount));
		addSignalCalc("cancelRatio", t ->
			new SignalCalculation("askCancelCount" + t, t, SUM, data -> data.askCancelCount));

		// mid price weighted by top 100 level sizes
		addSignalCalc("weightedMidRetSMA", t ->
			new SignalCalculation("weightedMidSMA" + t, t, SMA, data -> data.weightedMid100));
	}

	/**
	 * Computes signals derived from calculations of static and moving average data. This method serves as
	 * organizational convenience - there is no need to modify getInputVariables(); the calculation is
	 * in initSignalCalcs() and here. Things that don't fit can be put in computeExtraCalcs()
	 */
	private void computeDerivedCalcs() {
		variableMap.put("weightedMidRetLast", variableMap.get("weightedMidLast") / variableMap.get("lastPrice") - 1);

		IntFunction<Double> lagRetGenerator = t ->
			variableMap.get("lastPrice") / variableMap.get("price" + t + "h") - 1.0;
		putDerivedCalc("lagRet", "lagRet", lagRetGenerator);

		putDerivedCalc("bookRatioxRet", "lagRet", lagRetGenerator);
		putDerivedCalc("bookRatioxRet", "bookRatioxRet", t ->
			variableMap.get("bookRatio") * variableMap.get("lagRet" + t));

		putDerivedCalc("onBalVol", "onBalVol", t ->
			variableMap.get("sumVolxRet" + t) / variableMap.get("volume" + t));

		IntFunction<Double> RSIRatioGenerator = t -> {
			double sumDownChange = variableMap.get("sumDownChange" + t);
			return sumDownChange == 0 ? Double.NaN :
				variableMap.get("sumUpChange" + t) / variableMap.get("sumDownChange" + t);
		};
		putDerivedCalc("RSIRatio", "RSIRatio", RSIRatioGenerator);
		putDerivedCalc("RSIRatioxRet", "lagRet", lagRetGenerator);
		putDerivedCalc("RSIRatioxRet", "RSIRatio", RSIRatioGenerator);
		putDerivedCalc("RSIRatioxRet", "RSIRatioxRet", t ->
			variableMap.get("RSIRatio" + t) * variableMap.get("lagRet" + t));

		putDerivedCalc("tradeRatio", "tradeRatio", t -> variableMap.get("bidTradeCount" + t) /
			(variableMap.get("bidTradeCount" + t) + variableMap.get("askTradeCount" + t)));
		putDerivedCalc("newRatio", "newRatio", t ->
			(variableMap.get("newBidCount" + t) - variableMap.get("newAskCount" + t)) /
				variableMap.get("book5PctCount"));
		putDerivedCalc("cancelRatio", "cancelRatio", t -> (variableMap.get("bidCancelCount" + t)
			- variableMap.get("askCancelCount" + t)) / variableMap.get("book5PctCount"));

		putDerivedCalc("weightedMidRetSMA", "weightedMidRetSMA", t ->
			variableMap.get("weightedMidSMA" + t) / variableMap.get("lastPrice") - 1);
	}

	/**
	 * Calculations that don't fit the framework
	 */
	private void computeExtraCalcs(final ConsolidatedHistory consolidatedHistory, final Product product) {
		// lag BTC return
		final ConsolidatedSnapshot latest = consolidatedHistory.latest();
		final long latestTimeNanos = latest.getTimeNanos();
		final ProductSnapshot latestBtc = latest.getProductSnapshot(Product.BTC_USD);
		final double btcLastPrice = latestBtc == null ? Double.NaN : latestBtc.lastPrice;

		for(int i = 1; i <= 12; i++)
			if(calcAllSignals || signalsToUseSet.contains("btcRet" + i)) {
				double btcLagPrice = Double.NaN;
				for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder((i + 1) * 3600)) {
					if(consolidatedSnapshot.getTimeNanos() > latestTimeNanos - i * 3600000000000L)
						break;
					final ProductSnapshot btc = consolidatedSnapshot.getProductSnapshot(Product.BTC_USD);
					btcLagPrice = btc == null ? Double.NaN : btc.lastPrice;
				}
				variableMap.put("btcRet" + i, btcLagPrice / btcLastPrice - 1);
			}

		// intervals since max/min
		for(int i = 1; i <= 12; i++)
			if(calcAllSignals || signalsToUseSet.contains("timeToMaxMin" + i)) {
				double minPrice = Double.MAX_VALUE;
				double maxPrice = 0.0;
				int intervalsToMin = 0;
				int intervalsToMax = 0;
				for(ConsolidatedSnapshot consolidatedSnapshot : consolidatedHistory.inOrder(i * 3600)) {
					ProductSnapshot snapshot = consolidatedSnapshot.getProductSnapshot(product);
					if(snapshot == null) continue;

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
				variableMap.put("timeToMaxMin" + i, (double) (intervalsToMax - intervalsToMin));
			}
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
	public Map<String,Double> getInputVariables(final ConsolidatedHistory consolidatedHistory,
		final Product product)
	{
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
			if(snapshot == null) continue;

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
			if(snapshot == null) continue;

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
