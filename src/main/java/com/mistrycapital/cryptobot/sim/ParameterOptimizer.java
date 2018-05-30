package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.util.MCProperties;

import java.util.List;
import java.util.function.Function;

/**
 * Optimization methods used to find best parameter values
 */
public class ParameterOptimizer {
	/**
	 * Searches the given parameters for the parameter values that optimize the given function. Modifies properties
	 * with the optimal values.
	 *
	 * @return Function result at the optimal parameters
	 */
	public <T extends Comparable<T>> T optimize(MCProperties properties, List<ParameterSearchSpace> searchList,
		Function<MCProperties,T> function)
	{
		LadderSearchResult<T> searchResult = ladderSearch(properties, searchList, function, 0);
		for(int i = 0; i < searchList.size(); i++)
			properties.setProperty(searchList.get(i).parameterName, Double.toString(searchResult.optimalParameters[i]));
		return searchResult.functionResult;
	}

	private <T extends Comparable<T>> LadderSearchResult<T> ladderSearch(MCProperties properties,
		List<ParameterSearchSpace> searchList, Function<MCProperties,T> function, int index)
	{
		if(index >= searchList.size())
			return new LadderSearchResult<>(null, function.apply(properties));

		ParameterSearchSpace searchSpace = searchList.get(index);

		T bestFunctionResult = null;
		double[] bestParameterValues = new double[searchList.size() - index];
		for(int i = 0; i <= searchSpace.ladderPoints; i++) {
			final double parameterValue = searchSpace.lowerBound +
				i * (searchSpace.upperBound - searchSpace.lowerBound) / searchSpace.ladderPoints;

			properties.put(searchSpace.parameterName, Double.toString(parameterValue));
			final LadderSearchResult<T> recursiveResult =
				ladderSearch(properties, searchList, function, index + 1);

			if(bestFunctionResult == null || recursiveResult.functionResult.compareTo(bestFunctionResult) > 0) {
				bestFunctionResult = recursiveResult.functionResult;
				bestParameterValues[0] = parameterValue;
				for(int j = 1; j < bestParameterValues.length; j++)
					bestParameterValues[j] = recursiveResult.optimalParameters[j - 1];
			}
		}

		return new LadderSearchResult<>(bestParameterValues, bestFunctionResult);
	}

	private static class LadderSearchResult<T> {
		final double[] optimalParameters;
		final T functionResult;

		LadderSearchResult(double[] optimalParameters, T functionResult) {
			this.optimalParameters = optimalParameters;
			this.functionResult = functionResult;
		}
	}
}
