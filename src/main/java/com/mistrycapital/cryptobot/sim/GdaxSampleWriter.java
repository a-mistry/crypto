package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedData;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

import static com.mistrycapital.cryptobot.PeriodicEvaluator.calcNextIntervalMillis;

public class GdaxSampleWriter {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final SimTimeKeeper timeKeeper;
	private final OrderBookManager orderBookManager;
	private final DynamicTracker dynamicTracker;
	private final IntervalDataAppender intervalDataAppender;
	private final BlockingQueue<GdaxMessage> messageQueue;
	private volatile boolean done;
	private long nextIntervalMillis;

	public GdaxSampleWriter(SimTimeKeeper timeKeeper, OrderBookManager orderBookManager, DynamicTracker dynamicTracker,
		IntervalDataAppender intervalDataAppender, BlockingQueue<GdaxMessage> messageQueue)
	{
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		this.dynamicTracker = dynamicTracker;
		this.intervalDataAppender = intervalDataAppender;
		this.messageQueue = messageQueue;
		done = false;
	}

	/**
	 * Mark that there are no further messages to be read beyond what is in the queue
	 */
	public void markDone() {
		done = true;
	}

	public void sample() {
		int sampleCount = 0;
		long startMs = System.currentTimeMillis();
		while(true) {
			final GdaxMessage message = messageQueue.poll();
			if(message == null && done)
				break;
			if(message == null)
				continue;

			timeKeeper.advanceTime(message.getTimeMicros() * 1000L);
			message.process(orderBookManager);
			message.process(dynamicTracker);

			// check for next interval time
			if(nextIntervalMillis == 0) {
				nextIntervalMillis = calcNextIntervalMillis(timeKeeper.epochMs());
			} else if(timeKeeper.epochMs() >= nextIntervalMillis) {
				try {
					dynamicTracker.recordSnapshots();
					final ConsolidatedData consolidatedData =
						ConsolidatedData.getSnapshot(orderBookManager, dynamicTracker);
					intervalDataAppender.recordSnapshot(timeKeeper.epochMs(), consolidatedData);
				} catch(IOException e) {
					log.error("Could not store snapshot, time=" + timeKeeper.epochNanos() + " " + timeKeeper.iso8601(),
						e);
				}
				nextIntervalMillis = calcNextIntervalMillis(timeKeeper.epochMs());
				sampleCount++;
				if(sampleCount % 100 == 0) {
					log.debug("Processed " + sampleCount + " intervals in "
						+ (System.currentTimeMillis() - startMs) / 1000.0 + "s");
				}
			}
		}
	}
}