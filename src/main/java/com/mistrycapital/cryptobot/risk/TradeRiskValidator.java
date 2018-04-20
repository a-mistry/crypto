package com.mistrycapital.cryptobot.risk;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.List;

public class TradeRiskValidator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;

	public TradeRiskValidator(TimeKeeper timeKeeper, Accountant accountant) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
	}

	public List<TradeInstruction> validate(List<TradeInstruction> instructions) {
		// checks to implement
		// 1. No more than X trades in past 24 hours
		// 2. Do not trade more than we have
		// 3. Don't buy more than position limit in any currency

		// NEED TO UNIT TEST THESE!
//		if(instructions != null)
//			log.debug("Checking risk on " + instructions.size() + " trades at " + timeKeeper.iso8601());
		return instructions; // no op
	}
}
