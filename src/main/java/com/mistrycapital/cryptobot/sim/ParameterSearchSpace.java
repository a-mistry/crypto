package com.mistrycapital.cryptobot.sim;

public class ParameterSearchSpace {
	final String parameterName;
	final double lowerBound;
	final double upperBound;
	final int ladderPoints;

	ParameterSearchSpace(String parameterName, double lowerBound, double upperBound, int ladderPoints) {
		this.parameterName = parameterName;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.ladderPoints = ladderPoints;
	}
}
