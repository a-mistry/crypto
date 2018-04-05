package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.sim.GdaxMessageFileReader;
import com.mistrycapital.cryptobot.sim.GdaxMessageTranslator;
import com.mistrycapital.cryptobot.sim.GdaxSampleWriter;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.mistrycapital.cryptobot.PeriodicEvaluator.INTERVAL_SECONDS;
import static com.mistrycapital.cryptobot.PeriodicEvaluator.SECONDS_TO_KEEP;
import static com.mistrycapital.cryptobot.TradeCrypto.INTERVAL_FILE_NAME;

/**
 * Run sampler code to
 */
public class SampleData {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
	{
		Path dataDir = Paths.get(MCProperties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);

		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		IntervalDataAppender intervalAppender = new IntervalDataAppender(dataDir, INTERVAL_FILE_NAME, timeKeeper);
		DynamicTracker dynamicTracker = new DynamicTracker(SECONDS_TO_KEEP / INTERVAL_SECONDS);

		BlockingQueue<GdaxMessage> messageQueue = new LinkedBlockingQueue<>();
		BlockingQueue<String> messageStringQueue = new ArrayBlockingQueue<>(10000);
		GdaxSampleWriter writer = new GdaxSampleWriter(timeKeeper, orderBookManager, dynamicTracker, intervalAppender, messageQueue);
		GdaxMessageTranslator translator = new GdaxMessageTranslator(messageStringQueue, messageQueue, writer);
		GdaxMessageFileReader reader = new GdaxMessageFileReader(dataDir, messageStringQueue, translator);

		Thread readerThread = new Thread(reader);
		readerThread.setDaemon(true);
		readerThread.start();
		Thread translatorThread = new Thread(translator);
		translatorThread.setDaemon(true);
		translatorThread.start();

		writer.sample();
	}
}
