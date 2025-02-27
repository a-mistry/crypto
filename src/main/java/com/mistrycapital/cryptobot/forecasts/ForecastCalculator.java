package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.gdax.common.Product;

import java.util.Map;

public interface ForecastCalculator {
	/**
	 * Calculates the latest value of this forecast for the given product. If logger is not null, logs the value
	 * as well
	 * @return Latest forecast value
	 */
	double calculate(ConsolidatedHistory consolidatedHistory, Product product, ForecastAppender logger);

	/**
	 * Used to generate datasets for estimation
	 * @return All inputs used to calculate this forecast with the current history, as a map from name to value
	 */
	Map<String,Double> getInputVariables(ConsolidatedHistory consolidatedHistory, Product product);
}
