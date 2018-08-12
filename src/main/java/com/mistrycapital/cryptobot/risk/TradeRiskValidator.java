package com.mistrycapital.cryptobot.risk;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
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
	private final OrderBookManager orderBookManager;
	private final int max24HourTrades;
	private final Queue<Long> lastDayTradeTimesInNanos;

	public TradeRiskValidator(MCProperties properties, TimeKeeper timeKeeper, Accountant accountant,
		OrderBookManager orderBookManager)
	{
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
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

		log.debug("Checking risk on " + instructions.size() + " trades");

		instructions = validatePosition(instructions);
		instructions = validateSize(instructions);
		instructions = validateTradeCount(instructions);

		return instructions;
	}

	/**
	 * Validate that we are placing no more than the limit of trades in 24 hours. Allow selling even if limit
	 * hit but no further buying
	 */
	List<TradeInstruction> validateTradeCount(List<TradeInstruction> instructions) {
		long oneDayAgoTimeInNanos = timeKeeper.epochNanos() - 24 * 60 * 60 * 1000000000L;
		while(!lastDayTradeTimesInNanos.isEmpty() && lastDayTradeTimesInNanos.peek() < oneDayAgoTimeInNanos)
			lastDayTradeTimesInNanos.remove();

		List<TradeInstruction> validated = new ArrayList<>(instructions.size());
		for(TradeInstruction instruction : instructions) {
			if(instruction.getOrderSide() == OrderSide.BUY && lastDayTradeTimesInNanos.size() >= max24HourTrades) {
				log.debug("Skipping trade " + instruction + " because max trade count hit");
				continue; // trade count limit
			}

			lastDayTradeTimesInNanos.add(timeKeeper.epochNanos());
			validated.add(instruction);

			log.debug("Validated trade count " + instruction);
		}
		return validated;
	}

	/** Validate that we have the positions we are trying to trade */
	List<TradeInstruction> validatePosition(List<TradeInstruction> instructions) {
		List<TradeInstruction> validated = new ArrayList<>(instructions.size());
		BBO bbo = new BBO();
		for(final TradeInstruction instruction : instructions) {
			orderBookManager.getBook(instruction.getProduct()).recordBBO(bbo);

			boolean resized = false;
			double resizedAmount = 0.0;

			if(instruction.getOrderSide() == OrderSide.BUY &&
				instruction.getAmount() * bbo.askPrice > accountant.getAvailable(Currency.USD))
			{
				final var availableUsd = accountant.getAvailable(Currency.USD);
				resizedAmount = availableUsd / bbo.askPrice;
				resized = true;
			}

			if(instruction.getOrderSide() == OrderSide.SELL &&
				instruction.getAmount() > accountant.getAvailable(instruction.getProduct().getCryptoCurrency()))
			{
				resizedAmount = accountant.getAvailable(instruction.getProduct().getCryptoCurrency());
				resized = true;
			}

			if(!resized) {
				validated.add(instruction);
			} else {
				log.debug("Resizing " + instruction + " down to " + resizedAmount + " due to funds");
				if(resizedAmount <= 0)
					continue;
				validated.add(new TradeInstruction(instruction.getProduct(), resizedAmount, instruction.getOrderSide(),
					instruction.getAggression(), instruction.getForecast()));
			}

		}
		return validated;
	}

	/** Validate the size is sufficiently large (0.001) */
	List<TradeInstruction> validateSize(List<TradeInstruction> instructions) {
		return instructions.stream()
			.filter(instruction -> instruction.getAmount() > 0.001)
			.collect(Collectors.toList());
	}
}
