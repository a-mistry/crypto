package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.aggregatedata.ProductSnapshot;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

class ProductTracker implements GdaxMessageProcessor {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Product product;
	/** Current interval data */
	private IntervalData curInterval;
	// extra values we need to keep track of
	/** Last trade price of previous interval */
	private double prevLastPrice;
	/** Volume times price, used for VWAP calculation */
	private double volumeTimesPrice;

	ProductTracker(Product product) {
		this.product = product;
		curInterval = new IntervalData();
		prevLastPrice = Double.NaN;
		volumeTimesPrice = 0.0;
	}

	ProductTracker(Product product, ProductSnapshot lastSnapshot) {
		this(product);
		if(lastSnapshot != null) prevLastPrice = lastSnapshot.lastPrice;
	}

	private synchronized void recordNewOrder(final Open msg) {
		final double size = msg.getRemainingSize();
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.newBidCount++;
			curInterval.newBidSize += size;
		} else {
			curInterval.newAskCount++;
			curInterval.newAskSize += size;
		}
	}

	private synchronized void recordCancel(final Done msg) {
		final double size = msg.getRemainingSize();
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.bidCancelCount++;
			curInterval.bidCancelSize += size;
		} else {
			curInterval.askCancelCount++;
			curInterval.askCancelSize += size;
		}
	}

	private synchronized void recordTrade(final Match msg) {
		final double price = msg.getPrice();
		final double size = msg.getSize();
		curInterval.lastPrice = price;
		curInterval.volume += size;
		volumeTimesPrice += price * size;
		if(msg.getOrderSide() == OrderSide.BUY) {
			curInterval.bidTradeCount++;
			curInterval.bidTradeSize += size;
		} else {
			curInterval.askTradeCount++;
			curInterval.askTradeSize += size;
		}
	}

	public synchronized IntervalData snapshot() {
		// finalize current interval
		if(!Double.isNaN(prevLastPrice))
			curInterval.ret = curInterval.lastPrice / prevLastPrice - 1.0;
		curInterval.vwap = curInterval.volume > 0.0 ? volumeTimesPrice / curInterval.volume : Double.NaN;
		IntervalData retValue = curInterval;

		if(Double.isNaN(retValue.lastPrice))
			log.debug("NaN last price in product tracker for " + product);

		// set up next interval
		prevLastPrice = curInterval.lastPrice;
		volumeTimesPrice = 0.0;
		curInterval = new IntervalData();

		return retValue;
	}

	@Override
	public void process(final Book msg) {
		// nothing to do
	}

	@Override
	public void process(final Received msg) {
		// nothing to do
	}

	@Override
	public void process(final Open msg) {
		recordNewOrder(msg);
	}

	@Override
	public void process(final Done msg) {
		if(msg.getReason() == Reason.CANCELED && msg.isLimitOrder()) {
			recordCancel(msg);
		}
	}

	@Override
	public void process(final Match msg) {
		recordTrade(msg);
	}

	@Override
	public void process(final ChangeSize msg) {
		// nothing to do
	}

	@Override
	public void process(final ChangeFunds msg) {
		// nothing to do
	}

	@Override
	public void process(final Activate msg) {
		// nothing to do
	}
}