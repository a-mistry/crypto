package com.mistrycapital.cryptobot.sim;

public class ParameterSearchSpace {
	final String parameterName;
	final double lowerBound;
	final double upperBound;

	ParameterSearchSpace(String parameterName, double lowerBound, double upperBound) {
		this.parameterName = parameterName;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}
}
