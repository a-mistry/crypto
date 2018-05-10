package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.sim.SimRunner;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Simulate {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args) {
		MCLoggerFactory.setSimLogLevel();
		MCProperties properties = new MCProperties();

		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Reading samples from " + dataDir);

		SimRunner simRunner = new SimRunner(properties, dataDir);
		long startNanos = System.nanoTime();
		simRunner.run();
		log.debug("Simulation took " + (System.nanoTime() - startNanos) / 1000000000.0 + "s");
	}

}
