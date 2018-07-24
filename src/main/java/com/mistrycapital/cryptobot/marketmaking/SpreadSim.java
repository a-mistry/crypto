package com.mistrycapital.cryptobot.marketmaking;

import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.sim.GdaxMessageFileReader;
import com.mistrycapital.cryptobot.sim.GdaxMessageTranslator;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SpreadSim {
	private static final Logger log = MCLoggerFactory.getLogger();

	public static void main(String[] args)
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Reading data from " + dataDir);

		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		OrderBookManager orderBookManager = new OrderBookManager(timeKeeper);

		BlockingQueue<GdaxMessage> messageQueue = new LinkedBlockingQueue<>();
		BlockingQueue<String> messageStringQueue = new ArrayBlockingQueue<>(10000);
		SpreadTracker spreadTracker = new SpreadTracker(messageQueue, timeKeeper, orderBookManager);
		GdaxMessageTranslator translator = new GdaxMessageTranslator(messageStringQueue, messageQueue, spreadTracker);
		GdaxMessageFileReader reader = new GdaxMessageFileReader(dataDir, messageStringQueue, translator);

		Thread readerThread = new Thread(() -> {
			try {
				reader.readZipFile(dataDir.resolve("gdax-orders-2018-07-22.zip"));
				translator.markDone();
			} catch(IOException e) {
				throw new RuntimeException(e);
			}
		});
		readerThread.setDaemon(true);
		readerThread.start();
		Thread translatorThread = new Thread(translator);
		translatorThread.setDaemon(true);
		translatorThread.start();

		spreadTracker.run();
	}
}
