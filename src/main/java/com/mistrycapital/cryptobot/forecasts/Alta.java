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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static com.mistrycapital.cryptobot.forecasts.Alta.SignalType.EMA;
import static com.mistrycapital.cryptobot.forecasts.Alta.SignalType.SMA;
import static com.mistrycapital.cryptobot.forecasts.Alta.SignalType.STATIC;

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
		new String[] {"bookRatio"};

	public Alta(MCProperties properties) {
		coeffs = new double[Product.count][signalsToUse.length + 1];
		for(Product product : Product.FAST_VALUES) {
			int productIndex = product.getIndex();
			final String coeffsString = properties.getProperty("forecast.alta.coeffs." + product);
			log.debug(product + " coeffs " + coeffsString);
			if(coeffsString == null)
				throw new RuntimeException("Could not find coeffs for product " + product);
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
		this.maxLookbackHours = maxLookbackHours;
	}

	/**
	 * This method serves as organizational convenience. All calculations of signals are defined here and in
	 * computeDerivedCalcs(). The rest of the code merely calculates based on these definitions
	 */
	private void initSignalCalcs() {
		signalCalcs.add(new SignalCalculation("bookRatio", 0, STATIC, data ->
			((double) data.bidCount5Pct) / (data.bidCount5Pct + data.askCount5Pct)));
		signalCalcs.add(new SignalCalculation("bookEMA6", 6, EMA, data ->
			((double) data.bidCount5Pct) / (data.bidCount5Pct + data.askCount5Pct)));
		signalCalcs.add(new SignalCalculation("bookSMA6", 6, SMA, data ->
			((double) data.bidCount5Pct) / (data.bidCount5Pct + data.askCount5Pct)));
		signalCalcs.add(new SignalCalculation("lastPrice", 0, STATIC, data -> data.lastPrice));
		signalCalcs.add(new SignalCalculation("price2h", 2, STATIC, data -> data.lastPrice));
		signalCalcs.add(new SignalCalculation("price6h", 6, STATIC, data -> data.lastPrice));
	}

	/**
	 * Computes signals derived from calculations of static and moving average data. This method serves as
	 * organizational convenience - there is no need to modify getInputVariables(); the calculation is
	 * in initSignalCalcs() and here.
	 */
	private void computeDerivedCalcs() {
		final double lagRet2 = variableMap.get("lastPrice") / variableMap.get("price6h") - 1.0;
		variableMap.put("lagRet2", lagRet2);
		variableMap.put("lagRet6", variableMap.get("lastPrice") / variableMap.get("price6h") - 1.0);
		variableMap.put("bookRatioxRet", variableMap.get("bookRatio") * lagRet2);
		variableMap.put("bookSMAxRet", variableMap.get("bookSMA6") * lagRet2);
		variableMap.put("bookEMAxRet", variableMap.get("bookEMA6") * lagRet2);
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
					signalCalc.value = 1.0 / signalCalc.numDatapoints;
					break;

				case EMA:
					signalCalc.value = 2.0 / (signalCalc.numDatapoints + 1);
					break;
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

		return variableMap;
	}

	enum SignalType {
		/** Point in time signal */
		STATIC,
		/** Simple moving average */
		SMA,
		/** Exponential moving average */
		EMA
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
