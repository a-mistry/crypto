package com.mistrycapital.cryptobot.execution;

public interface ExecutionEngine {
	/**
	 * Execute the given trades, in the order in which they are given. A trade isn't started until all the previous
	 * trades have completed
	 *
	 * @param instructions Array of instructions
	 */
	void trade(TradeInstruction[] instructions);
}
