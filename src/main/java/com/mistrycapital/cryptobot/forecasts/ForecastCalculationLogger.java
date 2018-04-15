package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ForecastCalculationLogger {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Path logFile;
	private BufferedWriter writer;
	private boolean headersWritten;

	public ForecastCalculationLogger(TimeKeeper timeKeeper, Path logFile) {
		this.timeKeeper = timeKeeper;
		this.logFile = logFile;
		headersWritten = false;
	}

	public void open() {
		try {
			writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch(IOException e) {
			log.error("Could not open forecast calculation log", e);
		}
	}

	public void close() {
		try {
			writer.close();
		} catch(IOException e) {
			log.error("Could not close forecast calculation log", e);
		}
	}

	public void logCalculation(Object... columns) {
		try {
			if(!headersWritten) {
				headersWritten = true;
				writer.write(
					"date,unixTimestamp,product,midPrice,bidCount5Pct,book5PctCount,bidTradeCount,askTradeCount,b0,b1,b2,bookRatio,tradeRatio,forecast");
				writer.newLine();
			}
			writer.write(Arrays.stream(columns).map(Object::toString).collect(Collectors.joining(",")));
			writer.newLine();
		} catch(IOException e) {
			log.error("Could not record forecast calculation log", e);
		}
	}
}
