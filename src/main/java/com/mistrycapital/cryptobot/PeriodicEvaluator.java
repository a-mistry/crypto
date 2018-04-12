package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.ForecastAppender;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.tactic.Tactic;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

public class PeriodicEvaluator implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Intervalizer intervalizer;
	private final OrderBookManager orderBookManager;
	private final DynamicTracker dynamicTracker;
	private final IntervalDataAppender intervalDataAppender;
	private final ForecastAppender forecastAppender;
	private final ConsolidatedHistory consolidatedHistory;
	private final ForecastCalculator forecastCalculator;
	private final Tactic tactic;
	private final ExecutionEngine executionEngine;

	private long nextIntervalMillis;
	private double[] forecasts;

	public PeriodicEvaluator(TimeKeeper timeKeeper, Intervalizer intervalizer, OrderBookManager orderBookManager,
		DynamicTracker dynamicTracker, IntervalDataAppender intervalDataAppender, ForecastAppender forecastAppender,
		ForecastCalculator forecastCalculator, Tactic tactic, ExecutionEngine executionEngine)
	{
		this.timeKeeper = timeKeeper;
		this.intervalizer = intervalizer;
		this.orderBookManager = orderBookManager;
		this.dynamicTracker = dynamicTracker;
		this.intervalDataAppender = intervalDataAppender;
		this.forecastAppender = forecastAppender;
		this.forecastCalculator = forecastCalculator;
		this.tactic = tactic;
		this.executionEngine = executionEngine;
		forecasts = new double[Product.count];
		nextIntervalMillis = intervalizer.calcNextIntervalMillis(timeKeeper.epochMs());
		consolidatedHistory = new ConsolidatedHistory(intervalizer);
	}

	@Override
	public void run() {
		while(true) {
			try {
				long remainingMs = nextIntervalMillis - timeKeeper.epochMs();
				if(remainingMs <= 0) {
					log.trace("snapshot");
					evaluateInterval();
					final long timeMs = timeKeeper.epochMs();
					nextIntervalMillis = intervalizer.calcNextIntervalMillis(timeMs);
					remainingMs = nextIntervalMillis - timeMs;
				}

				// try to get as close as possible to the beginning of the interval
				final long sleepMs;
				if(remainingMs > 100) {
					sleepMs = remainingMs - 100;
				} else {
					sleepMs = remainingMs;
				}
				log.trace("Sleeping for " + sleepMs + "ms");
				Thread.sleep(sleepMs);

			} catch(InterruptedException e) {
				log.error("Sampling/trading thread interrupted", e);
			}
		}
	}

	void evaluateInterval() {
		// check that data is available on all books
		BBO bbo = new BBO();
		for(Product product : Product.FAST_VALUES) {
			orderBookManager.getBook(product).recordBBO(bbo);
			if(Double.isNaN(bbo.bidPrice) || Double.isNaN(bbo.askPrice))
				return; // need to wait for book data to become available before recording
		}

		// get data snapshot
		ConsolidatedSnapshot consolidatedSnapshot =
			ConsolidatedSnapshot.getSnapshot(orderBookManager, dynamicTracker, timeKeeper);
		consolidatedHistory.add(consolidatedSnapshot);

		// save to file
		try {
			intervalDataAppender.recordSnapshot(nextIntervalMillis, consolidatedSnapshot);
		} catch(IOException e) {
			log.error("Error saving interval data", e);
		}

		// update signals
		for(Product product : Product.FAST_VALUES) {
			forecasts[product.getIndex()] = forecastCalculator.calculate(consolidatedHistory, product);
		}
		try {
			forecastAppender.recordForecasts(nextIntervalMillis, forecasts);
		} catch(IOException e) {
			log.error("Error saving forecast data", e);
		}

		// possibly trade
		List<TradeInstruction> instructions = tactic.decideTrades(consolidatedSnapshot, forecasts);
		if(instructions != null && instructions.size() > 0)
			executionEngine.trade(instructions);
	}
}
