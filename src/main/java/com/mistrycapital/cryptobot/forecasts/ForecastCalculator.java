package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.gdax.common.Product;

public interface ForecastCalculator {
	/**
	 * Calculates the latest value of this forecast for the given product
	 * @return Latest forecast value
	 */
	double calculate(ConsolidatedHistory consolidatedHistory, Product product);
}
