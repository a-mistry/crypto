package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.gdax.websocket.Product;

public interface Forecast {
	/**
	 * Calculates the latest value of this forecast for the given product
	 * @return Latest forecast value
	 */
	double calculate(ConsolidatedData consolidatedData, Product product);
}
