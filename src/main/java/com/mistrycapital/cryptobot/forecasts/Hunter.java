package com.mistrycapital.cryptobot.forecasts;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Product;

/**
 * Extremely simple reversion strategy
 */
public class Hunter implements ForecastCalculator {
	@Override
	public double calculate(final ConsolidatedHistory consolidatedHistory, final Product product) {
		final double[] inputVariables = getInputVariables(consolidatedHistory, product);
		return -0.15 * inputVariables[0];
	}

	@Override
	public double[] getInputVariables(final ConsolidatedHistory consolidatedHistory, final Product product) {
		double lagRet = 0.0;
		int count = 0;
		for(ConsolidatedSnapshot snapshot : consolidatedHistory.values()) {
			lagRet += Math.log(1 + snapshot.getProductSnapshot(product).ret);
			count++;
			if(count >= 36) break;
		}
		return new double[] { lagRet };
	}

	@Override
	public String[] getInputVariableNames() {
		return new String[] { "lagRet" };
	}
}
