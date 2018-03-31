package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.forecasts.Forecast;
import com.mistrycapital.cryptobot.forecasts.Snowbird;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;

public class PeriodicEvaluator implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	/** Total amount of history to track (default 1 day) */
	public static final int SECONDS_TO_KEEP = MCProperties.getIntProperty("history.secondsToKeep", 60 * 60 * 24);
	/** Length of each interval of data aggregation */
	public static final int INTERVAL_SECONDS = MCProperties.getIntProperty("history.intervalSeconds", 60);

	private final TimeKeeper timeKeeper;
	private final OrderBookManager orderBookManager;
	private final DynamicTracker dynamicTracker;
	private final IntervalDataAppender intervalDataAppender;

	private long nextIntervalMillis;
	private Forecast snowbird;

	public PeriodicEvaluator(TimeKeeper timeKeeper, OrderBookManager orderBookManager, DynamicTracker dynamicTracker,
		IntervalDataAppender intervalDataAppender)
	{
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		this.dynamicTracker = dynamicTracker;
		this.intervalDataAppender = intervalDataAppender;
		snowbird = new Snowbird();
		nextIntervalMillis = calcNextIntervalMillis(timeKeeper.epochMs());
	}

	@Override
	public void run() {
		while(true) {
			try {
				final long timeMs = timeKeeper.epochMs();
				final long remainingMs = nextIntervalMillis - timeMs;
				if(remainingMs <= 0) {
					evaluateInterval();
					nextIntervalMillis = calcNextIntervalMillis(timeMs);
				} else if(remainingMs > 100) {
					Thread.sleep(remainingMs - 100);
				} else if(remainingMs > 20){
					Thread.sleep(10);
				} else {
					Thread.sleep(1);
				}

			} catch(InterruptedException e) {
				log.error("Sampling/trading thread interrupted", e);
			}
		}
	}

	void evaluateInterval() {
		// get data snapshot
		ConsolidatedData consolidatedData = ConsolidatedData.getSnapshot(orderBookManager, dynamicTracker);

		// save to file
		try {
			intervalDataAppender.recordSnapshot(timeKeeper.epochNanos() / 1000L, consolidatedData);
		} catch(IOException e) {
			log.error("Error saving interval data", e);
		}

		// update signals
		for(Product product : Product.FAST_VALUES) {
			double forecast = snowbird.calculate(consolidatedData, product);
			log.debug("Forecast " + product + " = " + forecast);
		}

		// possibly trade
	}

	final static long calcNextIntervalMillis(long curMillis) {
		return (curMillis / 1000L / INTERVAL_SECONDS + 1) * 1000L * INTERVAL_SECONDS;
	}

}
