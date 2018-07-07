package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.util.MCProperties;

import java.lang.reflect.InvocationTargetException;

public class ForecastFactory {
	/** @return ForecastCalculator constructed from the one specified in the forecast.calculator property */
	public static ForecastCalculator getCalculatorInstance(MCProperties properties) {
		try {
			String fcName = properties.getProperty("forecast.calculator");
			@SuppressWarnings("unchecked")
			Class<ForecastCalculator> fcClazz =
				(Class<ForecastCalculator>) Class.forName("com.mistrycapital.cryptobot.forecasts." + fcName);
			return fcClazz.getConstructor(MCProperties.class).newInstance(properties);
		} catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}
