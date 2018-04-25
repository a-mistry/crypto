package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonObject;

import java.time.Instant;

class JsonObjectUtil {
	public static double getOrDefault(JsonObject json, String propertyName, double defaultValue) {
		if(json.has(propertyName))
			return Double.parseDouble(json.get(propertyName).getAsString());
		return defaultValue;
	}

	public static boolean getOrDefault(JsonObject json, String propertyName, boolean defaultValue) {
		if(json.has(propertyName))
			return json.get(propertyName).getAsBoolean();
		return defaultValue;
	}

	public static final long parseTimeMicros(String timeString) {
		Instant instant = Instant.parse(timeString);
		return instant.getEpochSecond()*1000000 + instant.getNano()/1000;
	}
}
