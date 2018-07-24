package com.mistrycapital.cryptobot.marketmaking;

import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.book.TopOfBookSubscriber;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.GdaxMessage;
import com.mistrycapital.cryptobot.sim.DoneNotificationRecipient;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.BlockingQueue;

public class SpreadTracker implements Runnable, DoneNotificationRecipient, TopOfBookSubscriber {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final BlockingQueue<GdaxMessage> messageQueue;
	private final SimTimeKeeper timeKeeper;
	private final OrderBookManager orderBookManager;

	private boolean done = false;

	SpreadTracker(BlockingQueue<GdaxMessage> messageQueue, SimTimeKeeper timeKeeper,
		OrderBookManager orderBookManager)
	{
		this.messageQueue = messageQueue;
		this.timeKeeper = timeKeeper;
		this.orderBookManager = orderBookManager;
		orderBookManager.subscribe(this);
	}

	@Override
	public void markDone() {
		done = true;
	}

	@Override
	public void run() {
		int msgCount = 0;
		while(true) {
			final GdaxMessage message = messageQueue.poll();
			if(message == null && done)
				break;
			if(message == null) {
				try {
					Thread.sleep(1);
				} catch(InterruptedException e) {}
				continue;
			}

			timeKeeper.advanceTime(message.getTimeMicros() * 1000L);
			message.process(orderBookManager);

			if(msgCount++ % 100000 == 0) {
				log.debug("Processed " + msgCount + " messages, num >10bp " + num10bp +
					" num >20bp " + num20bp + " buy >10bp " + numBid10bp + " sell >10bp " + numAsk10bp);
			}
		}
	}

	// only eth for now
	private double prevBid = Double.NaN;
	private double prevAsk = Double.NaN;
	private double prevSpread = Double.NaN;
	private double widest = 0;
	private long num10bp = 0;
	private long num20bp = 0;
	private long numBid10bp = 0;
	private long numAsk10bp = 0;

	private int printCount = 0;

	@Override
	public void onChanged(final Product product, final OrderSide side, final double bidPrice, final double askPrice) {
//		System.err.println(product + " " + bidPrice + "-" + askPrice);
		if(product != Product.ETH_USD) return;
		final double spread = askPrice - bidPrice;
		if(Double.isNaN(spread)) return;
		final double spreadToMid = spread * 2 / (bidPrice + askPrice);
//		if(spread > widest) widest = spread;
//		if(spread > prevSpread && spreadToMid > 0.0010) num10bp++;
//		if(spread > prevSpread && spreadToMid > 0.0020) num20bp++;
//
//		if(spread > prevSpread && side == OrderSide.BUY && spreadToMid > 0.0010)
//			numBid10bp++;
//		if(spread > prevSpread && side == OrderSide.SELL && spreadToMid > 0.0010)
//			numAsk10bp++;

		if(printCount == 0 && spread > prevSpread && side == OrderSide.BUY && spreadToMid > 0.0020) {
			printCount = 30; // print next ticks
			System.err.println("-------------- BUY @ " + bidPrice);
		}
		if(printCount > 0) {
			printCount--;
			System.err.println(timeKeeper.iso8601() + " " + bidPrice + "-" + askPrice);
		}

		prevBid = bidPrice;
		prevAsk = askPrice;
		prevSpread = spread;
	}
}
