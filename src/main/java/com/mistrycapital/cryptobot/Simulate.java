package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.sim.SimRunner;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.time.Intervalizer;
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
		MCProperties properties = new MCProperties();
		final boolean shouldLogForecasts = properties.getBooleanProperty("sim.logForecasts", false);

		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Reading samples from " + dataDir);

		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		Intervalizer intervalizer = new Intervalizer(properties);
		ForecastAppender forecastAppender = new ForecastAppender(dataDir, FORECAST_FILE_NAME, timeKeeper);
		if(shouldLogForecasts) {
			forecastAppender.open();
		}

		SimRunner simRunner = new SimRunner(properties, intervalizer, dataDir, timeKeeper, forecastAppender);
		long startNanos = System.nanoTime();
		simRunner.run();
		log.debug("Simulation took " + (System.nanoTime()-startNanos)/1000000000.0 + "s");
	}

}
