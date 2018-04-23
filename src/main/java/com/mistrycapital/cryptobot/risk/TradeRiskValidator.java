package com.mistrycapital.cryptobot.risk;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class TradeRiskValidator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final int max24HourTrades;
	private final Queue<Long> lastDayTradeTimesInNanos;

	public TradeRiskValidator(MCProperties properties, TimeKeeper timeKeeper, Accountant accountant) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		max24HourTrades = properties.getIntProperty("risk.max24hourTrades", 20);
		lastDayTradeTimesInNanos = new ArrayDeque<>();
	}

	public List<TradeInstruction> validate(List<TradeInstruction> instructions) {
		// checks to implement
		// 1. No more than X trades in past 24 hours
		// 2. Do not trade more than we have
		// 3. Don't buy more than position limit in any currency

		// NEED TO UNIT TEST THESE!
		if(instructions == null) return null;

		return instructions;
	}

	private List<TradeInstruction> validateDisabled(List<TradeInstruction> instructions) {
		log.debug("Checking risk on " + instructions.size() + " trades at " + timeKeeper.iso8601());

		long oneDayAgoTimeInNanos = timeKeeper.epochNanos() - 24 * 60 * 60 * 1000000000L;
		while(!lastDayTradeTimesInNanos.isEmpty() && lastDayTradeTimesInNanos.peek() < oneDayAgoTimeInNanos)
			lastDayTradeTimesInNanos.remove();

		List<TradeInstruction> validated = new ArrayList<>(instructions.size());
		for(TradeInstruction instruction : instructions) {
			if(lastDayTradeTimesInNanos.size() >= max24HourTrades)
				continue; // trade count limit

			lastDayTradeTimesInNanos.add(timeKeeper.epochNanos());
			validated.add(instruction);

			log.debug("Validated trade " + instruction.getOrderSide() + " " + instruction.getAmount() + " " +
				instruction.getProduct() + " at" + timeKeeper.iso8601());
		}
		return validated;
	}
}
