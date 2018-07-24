package com.mistrycapital.cryptobot.sim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.BlockingQueue;

public class GdaxMessageTranslator implements Runnable, DoneNotificationRecipient {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final BlockingQueue<String> messageStringQueue;
	private final BlockingQueue<GdaxMessage> messageQueue;
	private final DoneNotificationRecipient writer;
	private final JsonParser jsonParser;
	private volatile boolean done;

	public GdaxMessageTranslator(BlockingQueue<String> messageStringQueue, BlockingQueue<GdaxMessage> messageQueue,
		DoneNotificationRecipient writer)
	{
		this.messageStringQueue = messageStringQueue;
		this.messageQueue = messageQueue;
		this.writer = writer;
		jsonParser = new JsonParser();
		done = false;
	}

	/**
	 * Mark that there are no further messages to be read beyond what is in the queue
	 */
	@Override
	public void markDone() {
		done = true;
	}

	private long readWaitMs = 0;

	@Override
	public void run() {
		while(true) {
			final String msgString = messageStringQueue.poll();
			if(msgString == null && done)
				break;
			if(msgString == null) {
				try {
					Thread.sleep(1);
					readWaitMs++;
					if(readWaitMs % 1000 == 0)
						log.debug("Waited " + (readWaitMs/1000) + "s in translator reader");
				} catch(InterruptedException e) {}
				continue;
			}

			try {
				GdaxMessage gdaxMessage = parseMessage(msgString);
				if(gdaxMessage != null) {
					if(messageQueue.remainingCapacity() == 0) {
						Thread.sleep(1);
					}
					messageQueue.put(gdaxMessage);
				}
			} catch(Exception e) {
				log.error("Error in parsing line: " + msgString, e);
			}
		}
		writer.markDone();
	}

	private GdaxMessage parseMessage(String msgStr) {
		// TODO: Reuse websocket code
		JsonObject json = jsonParser.parse(msgStr).getAsJsonObject();
		String type = json.get("type").getAsString();
		GdaxMessage message = null;
		switch(type) {
			case "book":
			case "order_book":
				// saved full book data
				message = new Book(json);
				break;
			case "open":
				// new order on book
				message = new Open(json);
				break;
			case "done":
				// order taken off book - could be filled or canceled
				message = new Done(json);
				break;
			case "match":
				// trade occurred
				message = new Match(json);
				break;
			case "change":
				// order modified
				if(json.has("new_size")) {
					message = new ChangeSize(json);
				} else {
					message = new ChangeFunds(json);
				}
				break;
			case "activate":
				// new stop order
				message = new Activate(json);
				break;
			case "received":
				// received message
				// fall through - we don't need these in sampling
			default:
				// this could be a new message type or received
				// or something else
		}
		json = null; // mark for GC
		return message;
	}
}
