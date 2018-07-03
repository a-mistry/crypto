package com.mistrycapital.cryptobot;

import ch.qos.logback.classic.Level;
import com.mistrycapital.cryptobot.forecasts.Alta;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
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

		ForecastCalculator forecastCalculator = new Alta(properties);
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

/*		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6"});
		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6", "weightedMidRet100", "weightedMidRet12h100"});

		String[] finalXs =
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6", "weightedMidRet100", "weightedMidRet12h100", "bookMA"};*/

		// This was the best out of all regressions using n-hour lag returns
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet1", "lagRet5"});

		// I don't fully believe that 1 and 5 hour lags matter due to instability of coeffs
		//runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet1", "lagRet5", "bookRatioxRet1", "bookRatioxRet5"});

		// 5 hour showed better in/out sample stability than 1 hour
		runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5"});

		// 6 hour EMA was best, but putting this on the back burner because of instability across products and samples
		runRegressionPrintResults(joined, "fut_ret_2h", new String[] {"lagRet5", "bookRatioxRet5", "bookEMA6xRet5"});

		// EMA was slightly better than SMA for upRatio
		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet5", "bookRatioxRet5", "bookEMA6xRet5", "upEMA6"});

		String[] finalXs = new String[] {"lagRet5", "bookRatioxRet5", "bookEMA6xRet5", "upEMA6"};
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
			System.out.println("MSE in  = " +
				calcMSE("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), joined));
			System.out.println("MSE out = " +
				calcMSE("fut_ret_2h", finalXs, finalResults.getParameterEstimates(), outSample));

			System.out.println("Product MSEs:");
			for(Product product : Product.FAST_VALUES) {
				System.out.println("MSE " + product + " in  = " +
					calcMSE("fut_ret_2h", finalXs, finalResults.getParameterEstimates(),
						joined.filter(key -> key.product == product)));
				System.out.println("MSE " + product + " out = " +
					calcMSE("fut_ret_2h", finalXs, finalResults.getParameterEstimates(),
						outSample.filter(key -> key.product == product)));
			}

			System.out.println("Out of sample product coeffs:");
			printProductCoeffs(outSample, "fut_ret_2h", finalXs);
		}

		printProductCoeffs(joined, "fut_ret_2h", finalXs);
	}

	static void printProductCoeffs(Table<TimeProduct> table, String y, String[] xs)
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
			coeffsStr += "forecast.snowbird.coeffs." + product + "=" + coeffString + "\n";
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

	static double calcMSE(String yCol, String[] xCols, double[] coeffs, Table<TimeProduct> table) {
		double sqErr = 0.0;
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
				n++;
			}
		}
		return sqErr / n;
	}
}
