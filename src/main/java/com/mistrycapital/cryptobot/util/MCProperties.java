package com.mistrycapital.cryptobot.util;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;

public class MCProperties extends Properties {
	private static final Logger log = MCLoggerFactory.getLogger();

	public MCProperties() {
		try {
			load(getClass().getClassLoader().getResourceAsStream("default.properties"));
		} catch(IOException e) {
			log.error("Could not load properties", e);
			System.exit(1);
		}
	}

	public int getIntProperty(String key) {
		return Integer.parseInt(getProperty(key));
	}

	public int getIntProperty(String key, int defaultValue) {
		return Integer.parseInt(getProperty(key, Integer.toString(defaultValue)));
	}

	public boolean getBooleanProperty(String key) {
		return Boolean.parseBoolean(getProperty(key));
	}

	public boolean getBooleanProperty(String key, boolean defaultValue) {
		return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
	}

	public double getDoubleProperty(String key) {
		return Double.parseDouble(getProperty(key));
	}

	public double getDoubleProperty(String key, double defaultValue) {
		return Double.parseDouble(getProperty(key, Double.toString(defaultValue)));
	}
}