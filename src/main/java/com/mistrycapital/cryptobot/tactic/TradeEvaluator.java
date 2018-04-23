package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.DailyAppender;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.appender.DecisionAppender;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

public class TradeEvaluator {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final ConsolidatedHistory consolidatedHistory;
	private final ForecastCalculator forecastCalculator;
	private final Tactic tactic;
	private final TradeRiskValidator tradeRiskValidator;
	private final ExecutionEngine executionEngine;
	private final DecisionAppender decisionAppender;
	private final DailyAppender dailyAppender;

	private double[] forecasts;

	/**
	 * Creates an evaluator
	 * Note that forecast appender and decision logger can be null if we do not want to log
	 */
	public TradeEvaluator(ConsolidatedHistory consolidatedHistory, ForecastCalculator forecastCalculator, Tactic tactic,
		TradeRiskValidator tradeRiskValidator, ExecutionEngine executionEngine,
		@Nullable DecisionAppender decisionAppender, @Nullable DailyAppender dailyAppender)
	{
		this.consolidatedHistory = consolidatedHistory;
		this.forecastCalculator = forecastCalculator;
		this.tactic = tactic;
		this.tradeRiskValidator = tradeRiskValidator;
		this.executionEngine = executionEngine;
		this.decisionAppender = decisionAppender;
		this.dailyAppender = dailyAppender;
		forecasts = new double[Product.count];
	}

	/**
	 * Evaluates forecasts and trades if warranted
	 */
	public void evaluate() {
		ConsolidatedSnapshot snapshot = consolidatedHistory.latest();

		// update signals
		for(Product product : Product.FAST_VALUES) {
			forecasts[product.getIndex()] = forecastCalculator.calculate(consolidatedHistory, product);
		}

		// possibly trade
		List<TradeInstruction> instructions = tradeRiskValidator.validate(tactic.decideTrades(snapshot, forecasts));
		if(instructions != null && instructions.size() > 0)
			executionEngine.trade(instructions);

		try {
			if(decisionAppender != null)
				decisionAppender.logDecision(snapshot, forecasts, instructions);
			if(dailyAppender != null)
				dailyAppender.writeDaily(snapshot);
		} catch(IOException e) {
			log.error("Error saving decision or daily data", e);
		}
	}
}
