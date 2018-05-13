package com.mistrycapital.cryptobot;

import ch.qos.logback.classic.Level;
import com.mistrycapital.cryptobot.forecasts.Brighton;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.regression.*;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
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

		ForecastCalculator forecastCalculator = new Brighton(properties);
		log.debug("Forecast calculator is " + forecastCalculator.getClass().getName());

		MCLoggerFactory.resetLogLevel(Level.INFO);
		DatasetGenerator datasetGenerator = new DatasetGenerator(properties, dataDir, forecastCalculator);
		Table<TimeProduct> forecastInputs = datasetGenerator.getForecastDataset();
		Table<TimeProduct> futureReturns = datasetGenerator.getReturnDataset();
		var joined = forecastInputs.join(futureReturns);

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

		var results =
			joined.regress("fut_ret_2h", new String[] {"lagRet", "bookRatioxRet", "cancelRatioxRet", "newRatioxRet"});
		System.out.println("N = " + results.getN());
		System.out.println("R^2 = " + results.getRSquared());
		System.out.println("Coeffs");
		Arrays.stream(results.getParameterEstimates())
			.forEach(System.out::println);
	}
}
