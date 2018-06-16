package com.mistrycapital.cryptobot;

import ch.qos.logback.classic.Level;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.regression.*;
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

public class AnalyzeData {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Read data from " + dataDir);

		ForecastCalculator forecastCalculator = new Snowbird(properties);
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
		var joined = forecastInputs.join(futureReturns)
			.filter(key -> key.timeInNanos > 1517448021000000000L); // discard Jan since it is spotty
		log.info("Joining data took " + (System.nanoTime() - startNanos) / 1000000.0 + "ms");
		startNanos = System.nanoTime();

		boolean writeOut = false;
		if(writeOut) {
			try(
				BufferedWriter out = Files.newBufferedWriter(dataDir.resolve("temp.csv"), StandardCharsets.UTF_8);
			) {
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

		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6"});
		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6","weightedMidRet100"});
		runRegressionPrintResults(joined, "fut_ret_2h",
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6","weightedMidRet100","weightedMidRet2h100"});
//		runRegressionPrintResults(joined, "fut_ret_2h",
//			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
//				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6",
//				"bookRatio", "tradeRatioxRet", "newRatioxRet", "cancelRatioxRet"});

		String[] finalXs =
			new String[] {"lagRet6", "bookRatioxRet", "upRatioxRet", "normVolxRet", "RSIRatioxRet", "tradeRatio",
				"newRatio", "cancelRatio", "timeToMaxMin", "lagBTCRet6", "weightedMidRet100", "weightedMidRet2h100"};

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
		System.out.println("       Variable     Coefficient       Std Error     T-stat");
		System.out.println("---------------  --------------  --------------  ---------");
		final var coeffs = results.getParameterEstimates();
		final var stdErrs = results.getStdErrorOfEstimates();
		for(int i = 0; i < coeffs.length; i++) {
			final var variableName = i == 0 ? "Constant" : xCols[i - 1];
			System.out.println(String.format("%15s %15.8f %15.8f %10.4f",
				variableName.substring(0, Math.min(variableName.length(), 15)),
				coeffs[i], stdErrs[i], coeffs[i] / stdErrs[i]));
		}
		return results;
	}
}
