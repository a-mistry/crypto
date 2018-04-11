package com.mistrycapital.cryptobot.execution;

import java.util.List;

public interface ExecutionEngine {
	/**
	 * Execute the given trades, in the order in which they are given. A trade isn't started until all the previous
	 * trades have completed
	 *
	 * @param instructions List of instructions
	 */
	void trade(List<TradeInstruction> instructions);
}
