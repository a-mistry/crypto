package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;

public class Tactic {
	private final Accountant accountant;

	public Tactic(Accountant accountant) {
		this.accountant = accountant;
	}

	/**
	 * Decides what trades to make, if any.
	 * @return Array of trades to make. Assumes trades will be executed in the same order as the array. Do not
	 * execute out of order or the trades may not be legitimate
	 */
	public TradeInstruction[] decideTrades(ConsolidatedSnapshot snapshot, double[] forecasts) {
		return new TradeInstruction[0];
	}
}
