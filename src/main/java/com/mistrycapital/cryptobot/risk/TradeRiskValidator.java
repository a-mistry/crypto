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
import java.util.stream.Collectors;

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
		// 4. Must trade in 0.001 increments

		// NEED TO UNIT TEST THESE!
		if(instructions == null) return null;

		instructions = validateTradeCount(instructions);
		instructions = validatePosition(instructions);
		instructions = validateSize(instructions);

		return instructions;
	}

	/** Validate that we are placing no more than the limit of trades in 24 hours */
	List<TradeInstruction> validateTradeCount(List<TradeInstruction> instructions) {
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
				instruction.getProduct() + " at " + timeKeeper.iso8601());
		}
		return validated;
	}

	/** Validate that we have the position we are trying to sell */
	List<TradeInstruction> validatePosition(List<TradeInstruction> instructions) {
		// TODO: implement this
		return instructions;
	}

	/** Validate the size is sufficiently large (0.001) */
	List<TradeInstruction> validateSize(List<TradeInstruction> instructions) {
		return instructions.stream()
			.filter(instruction -> instruction.getAmount()>0.001)
			.collect(Collectors.toList());
	}
}
