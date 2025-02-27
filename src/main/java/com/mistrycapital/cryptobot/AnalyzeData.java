package com.mistrycapital.cryptobot;

import ch.qos.logback.classic.Level;
import com.google.common.base.Joiner;
import com.mistrycapital.cryptobot.forecasts.Alta;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.ForecastFactory;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.regression.*;
import com.mistrycapital.cryptobot.sim.SampleTesting;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mistrycapital.cryptobot.sim.SampleTesting.SamplingType.IN_SAMPLE;

public class AnalyzeData {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		MCLoggerFactory.resetLogLevel(Level.INFO);
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.info("Read data from " + dataDir);

		// calculate or read cached forecast inputs
		final boolean readCachedForecasts = properties.getBooleanProperty("analysis.readCachedForecasts", false);
		final Table<TimeProduct> fullData;
		long startNanos = System.nanoTime();
		DatasetGenerator datasetGenerator = new DatasetGenerator(properties);
		if(!readCachedForecasts) properties.put("forecast.alta.calcAllSignals", "true");
		final ForecastCalculator forecastCalculator = ForecastFactory.getCalculatorInstance(properties);
		final String fcName = forecastCalculator.getClass().getSimpleName().toLowerCase(Locale.US);
		Path dataCacheFile = dataDir.resolve("cached-inputs-" + fcName + ".mc");
		if(readCachedForecasts) {
			log.info("Reading cached forecast inputs for " + fcName);
			fullData = datasetGenerator.readBinaryDataset(dataCacheFile);
		} else {
			log.info("Calculating forecast inputs from " + fcName);
			fullData = datasetGenerator.calcSignalDataset(dataDir, forecastCalculator)
				.filter(key -> key.timeInNanos > 1519862400L * 1000000000L); // discard pre-Mar since it is spotty
		}
		log.info("Loading data took " + (System.nanoTime() - startNanos) / 1000000000.0 + "sec");

		// save inputs for next time
		if(!readCachedForecasts) {
			startNanos = System.nanoTime();
			datasetGenerator.writeBinaryDataset(fullData, dataCacheFile);
			log.info("Writing cached data took " + (System.nanoTime() - startNanos) / 1000000000.0 + "sec");
		}


//		exploreRegressions(fullData);
//		optimizeVars(fullData);
//		fitFullData(fullData, fcName);
		compareInOut(fullData, fcName,
			new String[] {"bookSMA9", "onBalVol2", "RSIRatioxRet10", "btcRet2", "bookRatioxRet1", "newRatio9",
				"cancelRatio8", "tradeRatio12", "upRatio3", "RSIRatio3", "timeToMaxMin10", "lagRet5",
				"weightedMidRetLast"}
		);
	}

	/** Used to explore various regressions in sample */
	private static void exploreRegressions(Table<TimeProduct> data) {
		// Always explore in sample
		MCProperties properties = new MCProperties();
		properties.setProperty("sim.sampling.type", "IN_SAMPLE");
		SampleTesting sampleTesting = new SampleTesting(properties);

		long startNanos = System.nanoTime();
		final var sampleData = data.filter(key -> sampleTesting.isSampleValid(key.timeInNanos));
		log.info("Sampling data took " + (System.nanoTime() - startNanos) / 1000000000.0 + "sec");

		// The below was work and info leading up to the Alta forecast signals
		// This was the best out of all regressions using n-hour lag returns
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet1", "lagRet5"});

		// I don't fully believe that 1 and 5 hour lags matter due to instability of coeffs
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet1", "lagRet5", "bookRatioxRet1", "bookRatioxRet5"});

		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5"});

		// 5 hour showed better in/out sample stability than 1 hour
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5"});

		// Book SMA was better than EMA, 9h a little better than 6h (but could go back to 6h), xRet didn't help
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9"});

		// Up ratio SMA better than EMA, but neither provided a ton of lift. Consider taking out
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2"});

		// 10h OBV value was much better than 3h but I don't believe it based on out of sample MSE
		//runRegressionPrintResults(joined, "fut_ret_2h",
		//	new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3"});

		// RSI ratio did nothing without multiplying by return but with return out of sample MSE was horrible
		//runRegressionPrintResults(joined, "fut_ret_2h",
		//	new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "RSIRatio7xRet5"});

		// simple moving sum was better than EMA on trade ratio, multiplying by return didn't matter
		//runRegressionPrintResults(joined, "fut_ret_2h",
		//	new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "tradeRatio10"});

		// new ratio and cancel ratio has the exact same effect, so we can only include one
		//runRegressionPrintResults(joined, "fut_ret_2h",
		//	new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "tradeRatio10",
		//		"newRatio10"});

		// SMA of weighted mid ret had best impact, but last value "worked" in combination with the SMA in terms of R^2 but not out sample MSE
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9",
		//	"upRatio2", "onBalVol3", "tradeRatio10", "newRatio10", "weightedMidRetSMA3", "weightedMidRetLast"});

		// Lag BTC return is questionable when looking at MSEs but very strong in R^2
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9",
		//	"upRatio2", "onBalVol3", "tradeRatio10", "newRatio10", "weightedMidRetSMA3", "btcRet5"});

		// This works about as well as the full set
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"bookRatioxRet5", "bookSMA9",
		//	"onBalVol3", "tradeRatio10", "weightedMidRetSMA3", "btcRet5"});

		// This is the best I came up with from own exploration
//			finalXs = new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "tradeRatio10",
//				"newRatio10", "weightedMidRetSMA3", "btcRet5", "timeToMaxMin8"};
	}

	/** Optimizes the data over the set of variables we are considering */
	private static void optimizeVars(Table<TimeProduct> fullData)
		throws ColumnNotFoundException
	{
		MCProperties properties = new MCProperties();
		properties.setProperty("sim.sampling.type", "IN_SAMPLE");
		SampleTesting sampleTesting = new SampleTesting(properties);

		long startNanos = System.nanoTime();
		final var inSample = fullData.filter(key -> sampleTesting.isSampleValid(key.timeInNanos));
		final var outSample = fullData.filter(key -> !sampleTesting.isSampleValid(key.timeInNanos));
		log.info("Sampling data took " + (System.nanoTime() - startNanos) / 1000000000.0 + "sec");

		String[] searched = searchVarsBestFit(inSample, outSample,
			new String[] {"lagRet", "bookRatioxRet", "bookSMA", "onBalVol", "tradeRatio", "newRatio", "cancelRatio",
				"upRatio", "weightedMidRetSMA", "RSIRatio", "RSIRatioxRet", "btcRet", "timeToMaxMin"});
//		String[] searched =
//			new String[] {"onBalVol2", "bookRatioxRet1", "bookSMA3", "weightedMidRetSMA1", "btcRet1", "cancelRatio4",
//				"upRatio12", "newRatio6", "RSIRatioxRet2", "RSIRatio3", "timeToMaxMin10",
//				"lagRet5", "tradeRatio1"};
		String[] additional = new String[searched.length + 1];
		for(int i = 0; i < searched.length; i++)
			additional[i] = searched[i];
		additional[additional.length - 1] = "weightedMidRetLast";
		runRegressionPrintResults(outSample, "fut_ret_2h", additional);
		String[] best = dropVarsBestFit(outSample, inSample, additional); // flip samples for this test
		runRegressionPrintResults(inSample, "fut_ret_2h", best);
		System.out.println("Java array is");
		System.out.println(
			"new String[] {" + Stream.of(best).map(x -> "\"" + x + "\"").collect(Collectors.joining(",")) + "}");
	}

	/** Fit the full dataset for the given forecast */
	private static void fitFullData(Table<TimeProduct> fullData, String fcName)
		throws ColumnNotFoundException
	{
		final String[] finalXs;
		switch(fcName) {
			case "alta":
				finalXs = Alta.signalsToUse;
				break;

			case "snowbird":
				finalXs = Snowbird.signalsToUse;
				break;

			default:
				throw new RuntimeException("Unrecognized forecast " + fcName);
		}

		RegressionResults finalResults = runRegressionPrintResults(fullData, "fut_ret_2h", finalXs);
		System.out.println("Full sample fitted coeffs:");
		System.out.print("forecast." + fcName + ".coeffs.all=");
		System.out.println(Arrays.stream(finalResults.getParameterEstimates())
			.map(x -> Double.isNaN(x) ? 0.0 : x)
			.mapToObj(Double::toString)
			.collect(Collectors.joining(","))
		);
	}

	/** Compare in/out sample fit for the given signals */
	private static void compareInOut(Table<TimeProduct> fullData, String fcName, String[] finalXs)
		throws ColumnNotFoundException
	{
		MCProperties properties = new MCProperties();
		properties.setProperty("sim.sampling.type", "IN_SAMPLE");
		SampleTesting sampleTesting = new SampleTesting(properties);

		long startNanos = System.nanoTime();
		final var inSample = fullData.filter(key -> sampleTesting.isSampleValid(key.timeInNanos));
		final var outSample = fullData.filter(key -> !sampleTesting.isSampleValid(key.timeInNanos));
		log.info("Sampling data took " + (System.nanoTime() - startNanos) / 1000000000.0 + "sec");

		final var finalResults = runRegressionPrintResults(inSample, "fut_ret_2h", finalXs);

		System.out.println("In/out sample testing:");
		System.out.println("R^2 in  = " +
			calcRsq("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), inSample, inSample));
		System.out.println("R^2 out = " +
			calcRsq("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), outSample, inSample));
		SampleFit inSampleFit =
			calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), inSample);
		SampleFit outSampleFit =
			calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), outSample);
		System.out.println("MSE in = " + inSampleFit.mse + "\tout = " + outSampleFit.mse);
		System.out.println("MAE in = " + inSampleFit.mae + "\tout = " + outSampleFit.mae);
		System.out.println("Win ratio in = " + inSampleFit.winRatio + "\tout = " + outSampleFit.winRatio + " ret " +
			outSampleFit.retPos);
		System.out.println(
			"Win ratio (>10bp pred) in = " + inSampleFit.winRatio10bp + "\tout = " + outSampleFit.winRatio10bp +
				" ret " + outSampleFit.ret10bp);
		System.out.println(
			"Win ratio (>20bp pred) in = " + inSampleFit.winRatio20bp + "\tout = " + outSampleFit.winRatio20bp +
				" ret " + outSampleFit.ret20bp);
		System.out.println(
			"Win ratio (>30bp pred) in = " + inSampleFit.winRatio30bp + "\tout = " + outSampleFit.winRatio30bp +
				" ret " + outSampleFit.ret30bp);

		System.out.println("Product MSEs:");
		for(Product product : Product.FAST_VALUES) {
			inSampleFit = calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(),
				inSample.filter(key -> key.product == product));
			outSampleFit = calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(),
				outSample.filter(key -> key.product == product));
			System.out.println("MSE " + product + " in = " + inSampleFit.mse + "\tout = " + outSampleFit.mse);
			System.out.println("MAE " + product + " in = " + inSampleFit.mae + "\tout = " + outSampleFit.mae);
			System.out.println("Win ratio " + product + " in = " + inSampleFit.winRatio + "\tout = "
				+ outSampleFit.winRatio + " ret " + outSampleFit.retPos);
			System.out.println("Win ratio " + product + " (>10bp pred) in = " + inSampleFit.winRatio10bp + "\tout = " +
				outSampleFit.winRatio10bp + " ret " + outSampleFit.ret10bp);
			System.out.println("Win ratio " + product + " (>20bp pred) in = " + inSampleFit.winRatio20bp + "\tout = " +
				outSampleFit.winRatio20bp + " ret " + outSampleFit.ret20bp);
			System.out.println("Win ratio " + product + " (>30bp pred) in = " + inSampleFit.winRatio30bp + "\tout = " +
				outSampleFit.winRatio30bp + " ret " + outSampleFit.ret30bp);
		}

		System.out.println("Out of sample product coeffs:");
		printProductCoeffs(outSample, "fut_ret_2h", finalXs, fcName);

		printProductCoeffs(inSample, "fut_ret_2h", finalXs, fcName);
		System.out.println("Overall coeffs:");
		System.out.println("forecast." + fcName + ".coeffs.all=" +
			Arrays.stream(finalResults.getParameterEstimates())
				.map(x -> Double.isNaN(x) ? 0.0 : x)
				.mapToObj(Double::toString)
				.collect(Collectors.joining(","))
		);
	}

	/** Run regression for each product and output coeffs in property file format */
	private static void printProductCoeffs(Table<TimeProduct> table, String y, String[] xs, String fcName)
		throws ColumnNotFoundException
	{
		String coeffsStr = "";
		for(Product product : Product.FAST_VALUES) {
			final var prodData = table.filter(key -> key.product == product);
			RegressionResults results = prodData.regress(y, xs);
			System.out.println("Product " + product + " R-sq " + results.getRSquared());
			final var coeffString = Arrays.stream(results.getParameterEstimates())
				.map(x -> Double.isNaN(x) ? 0.0 : x)
				.mapToObj(Double::toString)
				.collect(Collectors.joining(","));
			coeffsStr += "forecast." + fcName + ".coeffs." + product + "=" + coeffString + "\n";
		}
		System.out.println(coeffsStr);
	}

	/**
	 * Searches correct period to use for each given variable. Method is to regress in sample and use out of sample fit
	 * across regressions to pick best window for each variable.
	 */
	private static String[] searchVarsBestFit(Table<TimeProduct> inSample, Table<TimeProduct> outSample,
		String[] searchVars)
		throws ColumnNotFoundException
	{
		final int[] bestIdx = new int[searchVars.length];
		Arrays.fill(bestIdx, 1);

		final var allCols = new String[searchVars.length];
		for(int i = 0; i < allCols.length; i++)
			allCols[i] = searchVars[i] + bestIdx[i];
		final var prevCols = new String[searchVars.length - 1];
		final var testCols = new String[12];

		boolean changed = true;
		for(int searchCount = 0; searchCount < 10 && changed; searchCount++) {
			changed = false;

			for(int metricIdx = 0; metricIdx < searchVars.length; metricIdx++) {
				for(int i = 0; i < prevCols.length; i++)
					if(i < metricIdx)
						prevCols[i] = searchVars[i] + bestIdx[i];
					else
						prevCols[i] = searchVars[i + 1] + bestIdx[i + 1];

				for(int i = 1; i <= 12; i++)
					testCols[i - 1] = searchVars[metricIdx] + i;

				String best =
					findBestFitVariable(inSample, outSample, "fut_ret_2h", prevCols, testCols, fit -> fit.retPos);
				final int prevBestIdx = bestIdx[metricIdx];
				bestIdx[metricIdx] = Integer.parseInt(best.replace(searchVars[metricIdx], ""));
				changed = changed || prevBestIdx != bestIdx[metricIdx];
			}
			log.info("After " + (searchCount + 1) + " iteration(s), variables are");
			for(int i = 0; i < searchVars.length; i++)
				log.info(searchVars[i] + bestIdx[i]);
		}

		String[] outVars = new String[searchVars.length];
		for(int i = 0; i < searchVars.length; i++)
			outVars[i] = searchVars[i] + bestIdx[i];
		return outVars;
	}

	/**
	 * Drops variables that don't help out of sample fit. Method is to drop variables, regress in sample, and compare
	 * out of sample fit to eliminate ones that do not contribute
	 */
	private static String[] dropVarsBestFit(Table<TimeProduct> inSample, Table<TimeProduct> outSample, String[] vars)
		throws ColumnNotFoundException
	{
		// set base
		final ToDoubleFunction<SampleFit> criterion = fit -> fit.numPos * fit.retPos;
		var maxCriterion = criterion.applyAsDouble(
			calcSampleFit("fut_ret_2h", vars, inSample.regress("fut_ret_2h", vars).getParameterEstimates(), outSample));
		log.info("Base objective is " + maxCriterion);

		// repeatedly drop vars
		Set<String> useVars = Arrays.stream(vars).collect(Collectors.toSet());
		boolean dropped = true;
		while(dropped) {
			dropped = false;
			String toDrop = null;

			for(final String var : useVars) {
				final String[] tempVars = new String[useVars.size() - 1];
				int i = 0;
				for(String tempVar : useVars)
					if(!var.equals(tempVar))
						tempVars[i++] = tempVar;

				final var results = inSample.regress("fut_ret_2h", tempVars);
				final var fit = calcSampleFit("fut_ret_2h", tempVars, results.getParameterEstimates(), outSample);
				final var objective = criterion.applyAsDouble(fit);
				log.info("Removing " + var + " objective=" + objective);

				if(objective > maxCriterion) {
					maxCriterion = objective;
					toDrop = var;
					break;
				}
			}

			if(toDrop != null) {
				log.info("Removed " + toDrop);
				useVars.remove(toDrop);
				dropped = true;
			}
		}

		log.info("Final set of vars is " + Joiner.on(',').join(useVars));
		return useVars.toArray(new String[0]);
	}

	/** Returns the variable that yields best fit (max criterion) of the variables to test */
	private static String findBestFitVariable(Table<TimeProduct> inSample, Table<TimeProduct> outSample, String yCol,
		String[] otherXCols, String[] varsToTest, ToDoubleFunction<SampleFit> criterion)
		throws ColumnNotFoundException
	{
		final String[] xCols = new String[otherXCols.length + 1];
		for(int i = 0; i < otherXCols.length; i++)
			xCols[i] = otherXCols[i];

		String best = null;
		double maxCriterion = Double.MIN_VALUE;

		for(String varToTest : varsToTest) {
			xCols[xCols.length - 1] = varToTest;
			final var results = inSample.regress(yCol, xCols);
			final var fit = calcSampleFit(yCol, xCols, results.getParameterEstimates(), outSample);
			final var objective = criterion.applyAsDouble(fit);
			System.out.println(varToTest + " R^2=" + results.getRSquared() + " Criterion=" + objective);

			if(objective > maxCriterion) {
				maxCriterion = objective;
				best = varToTest;
			}
		}
		System.out.println("Best was " + best);

		return best;
	}

	private static RegressionResults runRegressionPrintResults(Table<?> table, String yCol, String[] xCols)
		throws ColumnNotFoundException
	{
		final var results = table.regress(yCol, xCols);
		System.out.println("-----------------------------------------");
		System.out.println("Dependent var:  \t" + yCol);
		System.out.println("N:              \t" + results.getN());
		System.out.println("R^2:            \t" + String.format("%6.4f", results.getRSquared()));
		System.out.println("            Variable     Coefficient       Std Error     T-stat");
		System.out.println("--------------------  --------------  --------------  ---------");
		final var coeffs = results.getParameterEstimates();
		final var stdErrs = results.getStdErrorOfEstimates();
		for(int i = 0; i < coeffs.length; i++) {
			final var variableName = i == 0 ? "Constant" : xCols[i - 1];
			System.out.println(String.format("%20s %15.8f %15.8f %10.4f",
				variableName.substring(0, Math.min(variableName.length(), 20)),
				coeffs[i], stdErrs[i], coeffs[i] / stdErrs[i]));
		}
		return results;
	}

	private static double calcRsq(String yCol, String[] xCols, double[] coeffs, Table<TimeProduct> table,
		Table<TimeProduct> trainingTable)
	{
		double yMean = 0.0;
		int trainingN = 0;
		for(Row<TimeProduct> row : trainingTable) {
			final double y = row.getColumn(yCol);
			if(Double.isNaN(y)) continue;
			for(int i = 0; i < xCols.length; i++) {
				if(Double.isNaN(row.getColumn(xCols[i])))
					continue;
			}
			yMean += y;
			trainingN++;
		}
		yMean /= trainingN;

		double totalSumSq = 0.0;
		double residSumSq = 0.0;
		int n = 0;
		for(Row<TimeProduct> row : table) {
			final double y = row.getColumn(yCol);
			double yFitted = coeffs[0];
			for(int i = 0; i < xCols.length; i++) {
				yFitted += row.getColumn(xCols[i]) * coeffs[i + 1];
			}
			final double total = y - yMean;
			final double resid = y - yFitted;
			if(!Double.isNaN(resid)) {
				totalSumSq += total * total;
				residSumSq += resid * resid;
				n++;
			}
		}
		return 1.0 - residSumSq / totalSumSq;
	}

	private static SampleFit calcSampleFit(String yCol, String[] xCols, double[] coeffs, Table<TimeProduct> table) {
		double sqErr = 0.0;
		double absErr = 0.0;
		double[] thresholds = new double[] {0, 0.0010, 0.0020, 0.0030};
		int[] wins = new int[thresholds.length];
		int[] numAboveThreshold = new int[thresholds.length];
		double[] rets = new double[thresholds.length];
		int n = 0;
		for(Row<TimeProduct> row : table) {
			final double y = row.getColumn(yCol);
			double yFitted = coeffs[0];
			for(int i = 0; i < xCols.length; i++) {
				yFitted += row.getColumn(xCols[i]) * coeffs[i + 1];
			}
			final double resid = y - yFitted;
			if(!Double.isNaN(resid)) {
				sqErr += resid * resid;
				absErr += Math.abs(resid);
				for(int i = 0; i < thresholds.length; i++) {
					if(yFitted > thresholds[i]) {
						numAboveThreshold[i]++;
						if(y > 0) {
							wins[i]++;
							rets[i] += y;
						}
					}
				}
				n++;
			}
		}
		SampleFit sampleFit = new SampleFit();
		sampleFit.mse = sqErr / n;
		sampleFit.mae = absErr / n;
		sampleFit.winRatio = ((double) wins[0]) / numAboveThreshold[0];
		sampleFit.winRatio10bp = ((double) wins[1]) / numAboveThreshold[1];
		sampleFit.winRatio20bp = ((double) wins[2]) / numAboveThreshold[2];
		sampleFit.winRatio30bp = ((double) wins[3]) / numAboveThreshold[3];
		sampleFit.retPos = rets[0] / numAboveThreshold[0];
		sampleFit.ret10bp = rets[1] / numAboveThreshold[1];
		sampleFit.ret20bp = rets[2] / numAboveThreshold[2];
		sampleFit.ret30bp = rets[3] / numAboveThreshold[3];
		sampleFit.numPos = numAboveThreshold[0];
		return sampleFit;
	}

	private static class SampleFit {
		/** Mean squared error */
		double mse;
		/** Mean absolute error */
		double mae;
		/** Win ratio (% correct of predicted gains) */
		double winRatio;
		/** Win ratio above 10bp threshold */
		double winRatio10bp;
		/** Win ratio above 20bp threshold */
		double winRatio20bp;
		/** Win ratio above 30bp threshold */
		double winRatio30bp;
		/** Average return when positive */
		double retPos;
		/** Average return when above 10bp threshold */
		double ret10bp;
		/** Average return when above 20bp threshold */
		double ret20bp;
		/** Average return when above 30bp threshold */
		double ret30bp;
		/** Num datapoints when positive */
		int numPos;
	}
}
