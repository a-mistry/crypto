package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.sim.SimRunner;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mistrycapital.cryptobot.TradeCrypto.FORECAST_FILE_NAME;

public class Simulate {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
		throws Exception
	{
		Path dataDir = Paths.get(MCProperties.getProperty("dataDir"));
		log.debug("Reading samples from " + dataDir);

		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		ForecastAppender forecastAppender = new ForecastAppender(dataDir, FORECAST_FILE_NAME, timeKeeper);
		forecastAppender.open();

		SimRunner simRunner = new SimRunner(dataDir, timeKeeper, forecastAppender);
		simRunner.run();
	}

}
