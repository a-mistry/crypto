package com.mistrycapital.cryptobot.dynamic;

import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Reason;
import com.mistrycapital.cryptobot.gdax.websocket.*;

class ProductTracker implements GdaxMessageProcessor {
	/** Current interval data */
	private IntervalData curInterval;
	// extra values we need to keep track of
	/** Last trade price of previous interval */
	private double prevLastPrice;
	/** Volume times price, used for VWAP calculation */
	private double volumeTimesPrice;

	ProductTracker() {
		curInterval = new IntervalData();
		prevLastPrice = Double.NaN;
		volumeTimesPrice = 0.0;
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

	private synchronized void recordReceived(final Received msg) {
		if(msg.getClientOid() != null) {
			if(msg.getOrderSide() == OrderSide.BUY)
				curInterval.clientOidBuyCount++;
			else
				curInterval.clientOidSellCount++;
		}
	}

	public synchronized IntervalData snapshot() {
		// finalize current interval
		if(!Double.isNaN(prevLastPrice))
			curInterval.ret = curInterval.lastPrice / prevLastPrice - 1.0;
		curInterval.vwap = curInterval.volume > 0.0 ? volumeTimesPrice / curInterval.volume : Double.NaN;
		IntervalData retValue = curInterval;

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
		recordReceived(msg);
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