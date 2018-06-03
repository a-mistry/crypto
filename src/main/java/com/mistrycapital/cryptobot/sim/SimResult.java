package com.mistrycapital.cryptobot.sim;

import java.util.List;

class SimResult implements Comparable<SimResult> {
	final String searchObjective;
	final List<Double> dailyPositionValuesUsd;
	final double holdingPeriodReturn;
	final double dailyAvgReturn;
	final double dailyVolatility;
	final double sharpeRatio;
	final double winPct;
	final double lossPct;
	final double winLoss;
	final double gainLoss;
	final double maxLossStreak;
	final double dailyAvgTradeCount;
	final double pctDaysWithTrades;

	SimResult(String searchObjective, List<Double> dailyPositionValuesUsd, List<Integer> dailyTradeCount) {
		this.searchObjective = searchObjective;
		this.dailyPositionValuesUsd = dailyPositionValuesUsd;
		holdingPeriodReturn =
			dailyPositionValuesUsd.get(dailyPositionValuesUsd.size() - 1) / dailyPositionValuesUsd.get(0) - 1;

		final double[] dailyReturns = new double[dailyPositionValuesUsd.size() - 1];
		for(int i = 0; i < dailyReturns.length; i++)
			dailyReturns[i] = dailyPositionValuesUsd.get(i + 1) / dailyPositionValuesUsd.get(i) - 1;
		double dailySum = 0.0;
		for(final double val : dailyReturns)
			dailySum += val;
		dailyAvgReturn = dailySum / dailyReturns.length;

		dailySum = 0.0;
		for(final double val : dailyReturns) {
			final double diff = val - dailyAvgReturn;
			dailySum += diff * diff;
		}
		dailyVolatility = Math.sqrt(dailySum / dailyReturns.length);

		sharpeRatio = dailyVolatility != 0.0 ? Math.sqrt(365) * dailyAvgReturn / dailyVolatility : 0.0;

		int winCount = 0;
		int lossCount = 0;
		double totalGain = 0.0;
		double totalLoss = 0.0;
		double lossStreak = 0.0;
		double maxLossStreak = 0.0;
		for(final double val : dailyReturns) {
			if(val > 0.0) {
				winCount++;
				totalGain = (1 + totalGain) * (1 + val) - 1;
				lossStreak = 0.0;
			}
			if(val < 0.0) {
				lossCount++;
				totalLoss = (1 + totalLoss) * (1 - val) - 1;
				lossStreak = (1 + lossStreak) * (1 + val) - 1;
				maxLossStreak = lossStreak < maxLossStreak ? lossStreak : maxLossStreak;
			}
		}
		this.maxLossStreak = maxLossStreak;
		winPct = ((double) winCount) / dailyReturns.length;
		lossPct = ((double) lossCount) / dailyReturns.length;
		winLoss = lossPct == 0 ? 0 : winPct / lossPct;
		gainLoss = totalLoss == 0 ? 0 : totalGain / totalLoss;

		dailyAvgTradeCount =
			dailyTradeCount.stream().reduce(Integer::sum).get() / ((double) dailyTradeCount.size());
		pctDaysWithTrades = dailyTradeCount.stream().map(x -> x > 0 ? 1 : 0).reduce(Integer::sum).get() /
			((double) dailyTradeCount.size());
	}

	/**
	 * This is our searchObjective function
	 */
	public int compareTo(SimResult b) {
		switch(searchObjective) {
			case "return":
				return Double.compare(holdingPeriodReturn, b.holdingPeriodReturn);
			case "sharpe":
				return Double.compare(sharpeRatio, b.sharpeRatio);
			case "win":
				return Double.compare(winPct, b.winPct);
			case "winloss":
				return Double.compare(winLoss, b.winLoss);
			case "gainloss":
				return Double.compare(gainLoss, b.gainLoss);
			case "utility":
				return Double.compare(
					holdingPeriodReturn * winLoss,
					b.holdingPeriodReturn * b.winLoss
				);
			default:
				throw new RuntimeException("Invalid search objective " + searchObjective);
		}
	}

	public static String csvHeaderRow() {
		return "holdingPeriodReturn,dailyAvgReturn,dailyVolatility,sharpeRatio,winPct,lossPct,winLoss,gainLoss,maxLossStreak,dailyAvgTradeCount,pctDaysWithTrades";
	}

	public String toCSVString() {
		StringBuilder builder = new StringBuilder();
		builder.append(holdingPeriodReturn);
		builder.append(',');
		builder.append(dailyAvgReturn);
		builder.append(',');
		builder.append(dailyVolatility);
		builder.append(',');
		builder.append(sharpeRatio);
		builder.append(',');
		builder.append(winPct);
		builder.append(',');
		builder.append(lossPct);
		builder.append(',');
		builder.append(winLoss);
		builder.append(',');
		builder.append(gainLoss);
		builder.append(',');
		builder.append(maxLossStreak);
		builder.append(',');
		builder.append(dailyAvgTradeCount);
		builder.append(',');
		builder.append(pctDaysWithTrades);
		return builder.toString();
	}
}
