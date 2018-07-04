package com.mistrycapital.cryptobot;

import ch.qos.logback.classic.Level;
import com.mistrycapital.cryptobot.forecasts.Alta;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.regression.*;
import com.mistrycapital.cryptobot.sim.SampleTesting;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.mistrycapital.cryptobot.sim.SampleTesting.SamplingType.IN_SAMPLE;

public class AnalyzeData {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Read data from " + dataDir);

		final boolean useAlta = properties.getProperty("forecast.calculator").equalsIgnoreCase("Alta");
		final ForecastCalculator forecastCalculator;
		forecastCalculator = useAlta ? new Alta(properties) : new Snowbird(properties);
		String fcName = forecastCalculator.getClass().getSimpleName().toLowerCase(Locale.US);
		log.debug("Forecast calculator is " + forecastCalculator.getClass().getName());

		MCLoggerFactory.resetLogLevel(Level.INFO);
		DatasetGenerator datasetGenerator = new DatasetGenerator(properties, dataDir, forecastCalculator);
		long startNanos = System.nanoTime();
		Table<TimeProduct> forecastInputs = datasetGenerator.getForecastDataset();
		log.info("Loading data/calculating forecasts took " + (System.nanoTime() - startNanos) / 1000000.0 + "ms");
		startNanos = System.nanoTime();
		Table<TimeProduct> futureReturns = datasetGenerator.getReturnDataset();
		log.info("Loading returns took " + (System.nanoTime() - startNanos) / 1000000.0 + "ms");
		startNanos = System.nanoTime();

		SampleTesting sampleTesting = new SampleTesting(properties);
		var joined = forecastInputs.join(futureReturns)
			.filter(key ->
				key.timeInNanos > 1519862400L * 1000000000L                  // discard pre-Mar since it is spotty
					&& sampleTesting.isSampleValid(key.timeInNanos)        // filter in/out/full sample
			);
		log.info("Joining data took " + (System.nanoTime() - startNanos) / 1000000.0 + "ms");
		startNanos = System.nanoTime();

		boolean writeOut = false;
		if(writeOut) {
			try(
				BufferedWriter out = Files.newBufferedWriter(dataDir.resolve("temp.csv"), StandardCharsets.UTF_8);
			)
			{
				boolean first = true;
				for(Row<TimeProduct> row : joined) {
					if(first) {
						out.append("unixTimestamp,product," +
							Arrays.stream(row.getColumnNames()).collect(Collectors.joining(",")) + "\n");
						first = false;
					}

					out.append(row.getKey().timeInNanos + "," + row.getKey().product + "," +
						Arrays.stream(row.getColumnValues()).mapToObj(Double::toString)
							.collect(Collectors.joining(",")) +
						"\n");
				}
			}
		}
		log.info("Writing data took " + (System.nanoTime() - startNanos) / 1000000.0 + "ms");

		// previous version of Snowbird had no book MA
		//runRegressionPrintResults(joined, "fut_ret_2h",
		//	new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
		//		"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6", "weightedMidRet100", "weightedMidRet12h100"});


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

		final String[] finalXs;
		if(useAlta)
			finalXs = new String[] {"lagRet5", "bookRatioxRet5", "bookSMA9", "upRatio2", "onBalVol3", "tradeRatio10",
				"newRatio10", "weightedMidRetSMA3", "btcRet5", "timeToMaxMin8"};
		else
			finalXs =
				new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
					"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6", "weightedMidRet100",
					"weightedMidRet12h100", "bookMA"};

		RegressionResults finalResults = runRegressionPrintResults(joined, "fut_ret_2h", finalXs);

		if(sampleTesting.getSamplingType() == IN_SAMPLE) {
			// compare with out of sample
			final var outSample = forecastInputs.join(futureReturns)
				.filter(key ->
					key.timeInNanos > 1519862400L * 1000000000L                 // discard pre-Mar since it is spotty
						&& !sampleTesting.isSampleValid(key.timeInNanos)        // filter out sample
				);

			System.out.println("In/out sample testing:");
			System.out.println("R^2 in  = " +
				calcRsq("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), joined, joined));
			System.out.println("R^2 out = " +
				calcRsq("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), outSample, joined));
			SampleFit inSampleFit = calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), joined);
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
					joined.filter(key -> key.product == product));
				outSampleFit = calcSampleFit("fut_ret_2h", finalXs, finalResults.getParameterEstimates(),
					outSample.filter(key -> key.product == product));
				System.out.println("MSE " + product + " in = " + inSampleFit.mse + "\tout = " + outSampleFit.mse);
				System.out.println("MAE " + product + " in = " + inSampleFit.mae + "\tout = " + outSampleFit.mae);
				System.out.println(
					"Win ratio " + product + " in = " + inSampleFit.winRatio + "\tout = " + outSampleFit.winRatio);
			}

			System.out.println("Out of sample product coeffs:");
			printProductCoeffs(outSample, "fut_ret_2h", finalXs, fcName);
		}

		printProductCoeffs(joined, "fut_ret_2h", finalXs, fcName);
		System.out.println("Overall coeffs:");
		System.out.println("forecast." + fcName + ".coeffs.all=" +
			Arrays.stream(finalResults.getParameterEstimates())
				.map(x -> Double.isNaN(x) ? 0.0 : x)
				.mapToObj(Double::toString)
				.collect(Collectors.joining(","))
		);
	}

	static void printProductCoeffs(Table<TimeProduct> table, String y, String[] xs, String fcName)
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

	static RegressionResults runRegressionPrintResults(Table<?> table, String yCol, String[] xCols)
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

	static double calcRsq(String yCol, String[] xCols, double[] coeffs, Table<TimeProduct> table,
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

	static class SampleFit {
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
	}

	static SampleFit calcSampleFit(String yCol, String[] xCols, double[] coeffs, Table<TimeProduct> table) {
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
		return sampleFit;
	}
}
