package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;

import java.util.List;

/**
 * Determines trades based on our view of the world. Note that implementations are NOT thread safe.
 */
public interface Tactic {
	/**
	 * Given market data and tactic state, decides what trades if any to make.
	 *
	 * @return List of trades to execute (potentially in parallel), or null if nothing to do
	 */
	List<TradeInstruction> decideTrades(ConsolidatedSnapshot snapshot, double[] forecasts);

	/**
	 * Callback from the execution algo to notify the tactic of a fill. Note that instruction is
	 * the original instruction object, which can be used to identify the order.
	 */
	void notifyFill(final TradeInstruction instruction, Product product, OrderSide orderSide, double amount,
		double price);

	/**
	 * "Warms up" the tactic with the given interval data point. This is effectively the same as decideTrades
	 * but does not create trade instructions. This is useful to restore after a restart, although implementations
	 * are not guaranteed to take into account outstanding orders correctly or have the correct historical positions.
	 * It is only a rough approximation.
	 */
	void warmup(ConsolidatedSnapshot snapshot, double[] forecasts);
}
