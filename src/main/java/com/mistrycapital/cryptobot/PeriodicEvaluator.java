package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
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

	public PeriodicEvaluator(TimeKeeper timeKeeper, OrderBookManager orderBookManager, DynamicTracker dynamicTracker, IntervalDataAppender intervalDataAppender) {
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		this.dynamicTracker = dynamicTracker;
		this.intervalDataAppender = intervalDataAppender;
	}

	@Override
	public void run() {
		while(true) {
			try {
				Thread.sleep(INTERVAL_SECONDS*1000);
				// check if we need to evaluate
				ConsolidatedData consolidatedData = ConsolidatedData.getSnapshot(orderBookManager, dynamicTracker);
				intervalDataAppender.recordSnapshot(timeKeeper.epochNanos()/1000L, consolidatedData);

				// update signals
			} catch(InterruptedException e) {
				log.error("Sampling/trading thread interrupted", e);
			} catch(IOException e) {
				log.error("Error saving interval data", e);
			}
		}
	}

	final static long calcNextIntervalMicros(long curMicros) {
		return (curMicros / 1000000L / INTERVAL_SECONDS + 1) * 1000000L * INTERVAL_SECONDS;
//		try {
//			fileAppender.append(newData.toCSVString());
//		} catch(IOException e) {
//			log.error("Could not append interval data for " + product, e);
//		}
	}

}
