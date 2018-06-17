package com.mistrycapital.cryptobot;

import com.google.common.base.Joiner;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TieOut {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		// Create tie out report. Assumes Simulate has run to produce daily.csv

		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		Path credentialsFile = Paths.get(properties.getProperty("credentialsFile"));
		log.debug("Reading credentials from " + credentialsFile);
		Properties credentials = new Properties();
		try(Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			credentials.load(reader);
		}

		// get actual positions from database
		DBRecorder dbRecorder = new DBRecorder(null, null, null, "mistrycapital", credentials.getProperty("MysqlUser"),
			credentials.getProperty("MysqlPassword"));
		final SortedMap<ZonedDateTime,Double> positions = dbRecorder.getDailyPositionUsd();

		// get sim positions from daily file
		final String DAILY_FILE_NAME = properties.getProperty("output.filenameBase.daily", "daily");
		Path dailyFile = dataDir.resolve(DAILY_FILE_NAME + ".csv");
		final SortedMap<ZonedDateTime,Double> simPositions = getSimPositions(dailyFile);

		// produce position comparison
		Path reportFile = dataDir.resolve(properties.getProperty("output.filenameBase.tieout", "tieout") + ".csv");
		produceTieoutReport(reportFile, positions, simPositions);

		// calc correlations and RMSEs
		calcMetrics(reportFile);
	}

	private static SortedMap<ZonedDateTime,Double> getSimPositions(Path dailyFile)
		throws IOException
	{
		final SortedMap<ZonedDateTime,Double> map = new TreeMap<>();
		try(
			Reader in = Files.newBufferedReader(dailyFile);
			CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in)
		)
		{
			for(CSVRecord record : parser) {
				Instant instant = Instant.parse(record.get("date") + "T00:00:00Z");
				ZonedDateTime dateTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
				double position = Double.parseDouble(record.get("TotalPositionUsd"));
				map.put(dateTime, position);
			}
		}
		return map;
	}

	private static void produceTieoutReport(Path reportFile, SortedMap<ZonedDateTime,Double> positions,
		SortedMap<ZonedDateTime,Double> simPositions)
		throws IOException
	{
		try(
			BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardOpenOption.CREATE);
			CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL.withHeader(TieOutColumn.columnNames))
		)
		{
			ZonedDateTime now = ZonedDateTime.now();
			double adjSimPosition = 0.0;
			double simPosition = 0.0;
			double position = 0.0;
			Object[] columns = new Object[TieOutColumn.count];
			for(ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.parse("2018-06-07T00:00:00Z"), ZoneOffset.UTC);
				dateTime.isBefore(now);
				dateTime = dateTime.plus(Period.ofDays(1))) {

				if(!positions.containsKey(dateTime))
					continue;

				if(simPosition == 0.0) {
					simPosition = simPositions.get(dateTime);
					adjSimPosition = position = positions.get(dateTime);
				}
				final double simRet = simPositions.get(dateTime) / simPosition - 1;
				final double ret = positions.get(dateTime) / position - 1;
				adjSimPosition = adjSimPosition * (1 + simRet);
				simPosition = simPositions.get(dateTime);
				position = positions.get(dateTime);

				columns[TieOutColumn.DATE.ordinal()] = DateTimeFormatter.ISO_LOCAL_DATE.format(dateTime);
				columns[TieOutColumn.POSITION_USD.ordinal()] = position;
				columns[TieOutColumn.SIM_POSITION_USD.ordinal()] = adjSimPosition;
				columns[TieOutColumn.RETURN.ordinal()] = ret;
				columns[TieOutColumn.SIM_RETURN.ordinal()] = simRet;
				csvPrinter.printRecord(columns);
			}
		}
	}

	private static void calcMetrics(Path reportFile)
		throws IOException
	{
		List<Double> positions = new ArrayList<>();
		List<Double> simPositions = new ArrayList<>();
		List<Double> returns = new ArrayList<>();
		List<Double> simReturns = new ArrayList<>();
		try(
			Reader in = Files.newBufferedReader(reportFile);
			CSVParser parser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in)
		)
		{
			for(CSVRecord record : parser) {
				positions.add(Double.parseDouble(record.get(TieOutColumn.POSITION_USD.toString())));
				simPositions.add(Double.parseDouble(record.get(TieOutColumn.SIM_POSITION_USD.toString())));
				returns.add(Double.parseDouble(record.get(TieOutColumn.RETURN.toString())));
				simReturns.add(Double.parseDouble(record.get(TieOutColumn.SIM_RETURN.toString())));
			}
		}

		log.info("N obs:           \t" + positions.size());
		log.info("Position RMSE:   \t" + calcRMSE(positions, simPositions));
		log.info("Return RMSE:     \t" + calcRMSE(returns, simReturns));
		log.info("Position Correl: \t" + calcCorrelation(positions, simPositions));
		log.info("Return Correl:   \t" + calcCorrelation(returns, simReturns));

		// also output last 5 data points
		log.info("");
		log.info("Last 5 data points");
		log.info(Joiner.on(',').join(new String[] {
			TieOutColumn.POSITION_USD.toString(),
			TieOutColumn.SIM_POSITION_USD.toString(),
			TieOutColumn.RETURN.toString(),
			TieOutColumn.SIM_RETURN.toString()
		}));
		for(int i=positions.size()-5; i<positions.size(); i++) {
			log.info(Joiner.on(',').join(Arrays.asList(
				positions.get(i),
				simPositions.get(i),
				returns.get(i),
				simReturns.get(i)
			)));
		}
	}

	private static double calcRMSE(List<Double> x, List<Double> y) {
		double sse = 0.0;
		for(int i = 0; i < x.size(); i++) {
			final double err = x.get(i) - y.get(i);
			sse += err * err;
		}
		return Math.sqrt(sse / x.size());
	}

	/** @return Pearson correlation coefficient */
	private static double calcCorrelation(List<Double> x, List<Double> y) {
		double xsq = 0.0;
		double ysq = 0.0;
		double xy = 0.0;
		double xSum = 0.0;
		double ySum = 0.0;
		final int n = x.size();
		for(int i = 0; i < n; i++) {
			final double xVal = x.get(i);
			final double yVal = y.get(i);
			xsq += xVal * xVal;
			ysq += yVal * yVal;
			xy += xVal * yVal;
			xSum += xVal;
			ySum += yVal;
		}
		final double xMean = xSum / n;
		final double yMean = ySum / n;
		final double covXY = xy - n * xMean * yMean;
		final double stdDevX = Math.sqrt(xsq - n * xMean * xMean);
		final double stdDevY = Math.sqrt(ysq - n * yMean * yMean);
		return covXY / (stdDevX * stdDevY);
	}

	enum TieOutColumn {
		DATE("date"),
		POSITION_USD("position_usd"),
		SIM_POSITION_USD("sim_position_usd"),
		RETURN("ret"),
		SIM_RETURN("sim_ret");

		private final String columnName;

		TieOutColumn(String columnName) {
			this.columnName = columnName;
		}

		@Override
		public String toString() {
			return columnName;
		}

		public static int count = values().length;
		public static String[] columnNames;

		static {
			columnNames = new String[count];
			for(TieOutColumn column : values()) { columnNames[column.ordinal()] = column.toString(); }
		}
	}
}
