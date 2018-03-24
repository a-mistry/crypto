package com.mistrycapital.cryptobot.util;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;

public class MCProperties {
	private static final Logger log = MCLoggerFactory.getLogger();
	private static final Properties properties = new Properties();

	static {
		try {
			properties.load(MCProperties.class.getClassLoader().getResourceAsStream("default.properties"));
		} catch(IOException e) {
			log.error("Could not load properties", e);
			System.exit(1);
		}
	}

	public static String getProperty(String key) {
		return properties.getProperty(key);
	}
	
	public static String getProperty(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}
}