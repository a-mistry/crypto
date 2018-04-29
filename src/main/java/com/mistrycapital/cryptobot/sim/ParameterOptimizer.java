package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.util.MCProperties;

import java.util.List;
import java.util.function.Function;

/**
 * Optimization methods used to find best parameter values
 */
public class ParameterOptimizer {
	/** Number of points to divide interval by in ladder search (actual iterations is ladderPoints+1) */
	private int ladderPoints;

	ParameterOptimizer(int ladderPoints) {
		this.ladderPoints = ladderPoints;
	}

	/**
	 * Searches the given parameters for the optimal parameter values. Modifies properties with the
	 * optimal values.
	 * @return Function result at the optimal parameters
	 */
	public double optimize(MCProperties properties, List<ParameterSearch> searchList,
		Function<MCProperties,Double> function)
	{
		return ladderSearch(searchList, properties, function, 0);
	}

	private double ladderSearch(List<ParameterSearch> searchList, MCProperties properties,
		Function<MCProperties,Double> function, int index)
	{
		// TODO: Fix this code. It is not correct; later values are overwritten by different numbers
		if(index >= searchList.size())
			return function.apply(properties);

		ParameterSearch parameter = searchList.get(index);

		double bestFunctionResult = Double.NEGATIVE_INFINITY;
		double bestPropertyValue = Double.NaN;
		for(int i = 0; i <= ladderPoints; i++) {
			final double propertyValue =
				parameter.lowerBound + i * (parameter.upperBound - parameter.lowerBound) / ladderPoints;

			properties.put(parameter.parameterName, Double.toString(propertyValue));
			final double functionResult = ladderSearch(searchList, properties, function, index + 1);

			if(functionResult > bestFunctionResult) {
				bestFunctionResult = functionResult;
				bestPropertyValue = propertyValue;
			}
		}

		properties.put(parameter.parameterName, Double.toString(bestPropertyValue));
		return bestFunctionResult;
	}

	static class ParameterSearch {
		final String parameterName;
		final double lowerBound;
		final double upperBound;

		ParameterSearch(String parameterName, double lowerBound, double upperBound) {
			this.parameterName = parameterName;
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
		}
	}
}
