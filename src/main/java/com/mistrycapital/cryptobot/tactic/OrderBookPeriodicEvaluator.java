package com.mistrycapital.cryptobot.tactic;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedHistory;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.appender.DailyAppender;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.execution.ExecutionEngine;
import com.mistrycapital.cryptobot.forecasts.ForecastCalculator;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.appender.DecisionAppender;
import com.mistrycapital.cryptobot.risk.TradeRiskValidator;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;

public class OrderBookPeriodicEvaluator implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Intervalizer intervalizer;
	private final Accountant accountant;
	private final OrderBookManager orderBookManager;
	private final DynamicTracker dynamicTracker;
	private final IntervalDataAppender intervalDataAppender;
	private final ConsolidatedHistory consolidatedHistory;
	private final TradeEvaluator tradeEvaluator;

	private long nextIntervalMillis;
	private long nextHourMillis;

	public OrderBookPeriodicEvaluator(TimeKeeper timeKeeper, Intervalizer intervalizer, Accountant accountant,
		OrderBookManager orderBookManager, DynamicTracker dynamicTracker, IntervalDataAppender intervalDataAppender,
		ForecastCalculator forecastCalculator, Tactic tactic, TradeRiskValidator tradeRiskValidator,
		ExecutionEngine executionEngine, DecisionAppender decisionAppender, DailyAppender dailyAppender)
	{
		this.timeKeeper = timeKeeper;
		this.intervalizer = intervalizer;
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
		this.dynamicTracker = dynamicTracker;
		this.intervalDataAppender = intervalDataAppender;
		nextIntervalMillis = intervalizer.calcNextIntervalMillis(timeKeeper.epochMs());
		nextHourMillis = intervalizer.calcNextHourMillis(timeKeeper.epochMs());
		consolidatedHistory = new ConsolidatedHistory(intervalizer);
		tradeEvaluator = new TradeEvaluator(consolidatedHistory, forecastCalculator, tactic, tradeRiskValidator,
			executionEngine, decisionAppender, dailyAppender);
	}

	@Override
	public void run() {
		while(true) {
			try {
				if(timeKeeper.epochMs() >= nextHourMillis) {
					accountant.refreshPositions();
					nextHourMillis = intervalizer.calcNextHourMillis(timeKeeper.epochMs());
				}

				long remainingMs = nextIntervalMillis - timeKeeper.epochMs();
				if(remainingMs <= 0) {
					log.trace("snapshot");
					if(recordData())
						tradeEvaluator.evaluate();
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

	/**
	 * Record snapshots and save to file, only for prod
	 *
	 * @return true if successfully recorded, false if data is not yet available
	 */
	boolean recordData() {
		// check that data is available on all books
		BBO bbo = new BBO();
		for(Product product : Product.FAST_VALUES) {
			orderBookManager.getBook(product).recordBBO(bbo);
			if(Double.isNaN(bbo.bidPrice) || Double.isNaN(bbo.askPrice))
				return false; // need to wait for book data to become available before recording
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

		return true;
	}
}
