package com.mistrycapital.cryptobot.dynamic;

import java.time.format.DateTimeFormatter;

/**
 * Dynamic product data for a given interval
 */
public class IntervalData {
	/** Last trade price */
	public double lastPrice;
	/** Return over interval */
	public double ret;
	/** Volume */
	public double volume;
	/** VWAP */
	public double vwap;
	/** Number of trades executed at the bid */
	public long bidTradeCount;
	/** Number of trades executed at the ask */
	public long askTradeCount;
	/** Size of trades executed at the bid */
	public double bidTradeSize;
	/** Size of trades executed at the ask */
	public double askTradeSize;
	/** Number of new bids placed */
	public long newBidCount;
	/** Number of new asks placed */
	public long newAskCount;
	/** Size of new bids placed */
	public double newBidSize;
	/** Size of new asks placed */
	public double newAskSize;
	/** Number of bids canceled */
	public long bidCancelCount;
	/** Number of asks canceled */
	public long askCancelCount;
	/** Size of bids canceled */
	public double bidCancelSize;
	/** Size of asks canceled */
	public double askCancelSize;

	IntervalData() {
		lastPrice = Double.NaN;
		ret = Double.NaN;
	}

	public static String csvHeaderRow() {
		return "last_price,ret_5m,volume,vwap,"
			+ "bid_trade_count,ask_trade_count,bid_trade_size,ask_trade_size,"
			+ "new_bid_count,new_ask_count,new_bid_size,new_ask_size,"
			+ "bid_cancel_count,ask_cancel_count,bid_cancel_size,ask_cancel_size";
	}

	private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public String toCSVString() {
		StringBuilder builder = new StringBuilder();
		builder.append(lastPrice);
		builder.append(',');
		builder.append(ret);
		builder.append(',');
		builder.append(volume);
		builder.append(',');
		builder.append(vwap);
		builder.append(',');
		builder.append(bidTradeCount);
		builder.append(',');
		builder.append(askTradeCount);
		builder.append(',');
		builder.append(bidTradeSize);
		builder.append(',');
		builder.append(askTradeSize);
		builder.append(',');
		builder.append(newBidCount);
		builder.append(',');
		builder.append(newAskCount);
		builder.append(',');
		builder.append(newBidSize);
		builder.append(',');
		builder.append(newAskSize);
		builder.append(',');
		builder.append(bidCancelCount);
		builder.append(',');
		builder.append(askCancelCount);
		builder.append(',');
		builder.append(bidCancelSize);
		builder.append(',');
		builder.append(askCancelSize);
		return builder.toString();
	}
}