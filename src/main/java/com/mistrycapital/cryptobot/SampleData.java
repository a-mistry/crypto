package com.mistrycapital.cryptobot;

import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.sim.GdaxMessageFileReader;
import com.mistrycapital.cryptobot.sim.GdaxMessageTranslator;
import com.mistrycapital.cryptobot.sim.GdaxSampleWriter;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Run sampler code to
 */
public class SampleData {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		final String INTERVAL_FILE_NAME = properties.getProperty("output.filenameBase.samples","samples");

		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);
		IntervalDataAppender intervalAppender = new IntervalDataAppender(dataDir, INTERVAL_FILE_NAME, timeKeeper);
		DynamicTracker dynamicTracker = new DynamicTracker();
		Intervalizer intervalizer = new Intervalizer(properties);

		BlockingQueue<GdaxMessage> messageQueue = new LinkedBlockingQueue<>();
		BlockingQueue<String> messageStringQueue = new ArrayBlockingQueue<>(10000);
		GdaxSampleWriter writer = new GdaxSampleWriter(timeKeeper, intervalizer, orderBookManager, dynamicTracker, intervalAppender, messageQueue);
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
