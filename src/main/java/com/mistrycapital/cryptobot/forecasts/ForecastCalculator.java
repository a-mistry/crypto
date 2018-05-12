package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.gdax.common.Product;

public interface ForecastCalculator {
	/**
	 * Calculates the latest value of this forecast for the given product
	 * @return Latest forecast value
	 */
	double calculate(ConsolidatedHistory consolidatedHistory, Product product);

	/**
	 * Used to generate datasets for estimation
	 * @return All inputs used to calculate this forecast with the current history
	 */
	double[] getInputVariables(ConsolidatedHistory consolidatedHistory, Product product);

	/**
	 * @return Column names of the input variables
	 */
	String[] getInputVariableNames();
}
