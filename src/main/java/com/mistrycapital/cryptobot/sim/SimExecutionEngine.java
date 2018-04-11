package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;

public class SimExecutionEngine implements ExecutionEngine {
	private ConsolidatedSnapshot snapshot;

	/** Store the given snapshot and use the quotes for fill prices */
	public void useSnapshot(ConsolidatedSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	@Override
	public void trade(final TradeInstruction[] instructions) {
		// no op for now
	}
}
