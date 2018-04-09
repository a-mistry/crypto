package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import org.apache.commons.csv.CSVRecord;

/**
 * Snapshot of product information (static book data and dynamic data recorded over the last interval)
 */
public class ProductSnapshot {
	private static final int DEPTH_1PCT = 0;
	private static final int DEPTH_5PCT = 1;

	/** Product */
	public final Product product;

	/** Bid price */
	public final double bidPrice;
	/** Ask price */
	public final double askPrice;
	/** Mid price */
	public final double midPrice;
	/** Quantity at bid */
	public final double bidSize;
	/** Quantity at ask */
	public final double askSize;

	/** Number of bids within 1% of mid */
	public final int bidCount1Pct;
	/** Number of asks within 1% of mid */
	public final int askCount1Pct;
	/** Number of bids within 5% of mid */
	public final int bidCount5Pct;
	/** Number of asks within 5% of mid */
	public final int askCount5Pct;
	/** Size of bids within 1% of mid */
	public final double bidSize1Pct;
	/** Size of asks within 1% of mid */
	public final double askSize1Pct;
	/** Size of bids within 5% of mid */
	public final double bidSize5Pct;
	/** Size of asks within 5% of mid */
	public final double askSize5Pct;

	/** Last trade price */
	public final double lastPrice;
	/** Return over interval */
	public final double ret;
	/** Volume */
	public final double volume;
	/** VWAP */
	public final double vwap;
	/** Number of trades executed at the bid */
	public final long bidTradeCount;
	/** Number of trades executed at the ask */
	public final long askTradeCount;
	/** Size of trades executed at the bid */
	public final double bidTradeSize;
	/** Size of trades executed at the ask */
	public final double askTradeSize;
	/** Number of new bids placed */
	public final long newBidCount;
	/** Number of new asks placed */
	public final long newAskCount;
	/** Size of new bids placed */
	public final double newBidSize;
	/** Size of new asks placed */
	public final double newAskSize;
	/** Number of bids canceled */
	public final long bidCancelCount;
	/** Number of asks canceled */
	public final long askCancelCount;
	/** Size of bids canceled */
	public final double bidCancelSize;
	/** Size of asks canceled */
	public final double askCancelSize;

	public static ProductSnapshot getSnapshot(Product product, OrderBook orderBook, IntervalData intervalData) {
		BBO bbo = new BBO();
		Depth[] depths = new Depth[2];
		depths[DEPTH_1PCT] = new Depth();
		depths[DEPTH_1PCT].pctFromMid = 0.01;
		depths[DEPTH_5PCT] = new Depth();
		depths[DEPTH_5PCT].pctFromMid = 0.05;
		orderBook.recordDepthsAndBBO(bbo, depths);

		return new ProductSnapshot(product, bbo, depths[0], depths[1], intervalData);
	}

	private ProductSnapshot(Product product, BBO bbo, Depth depth1Pct, Depth depth5Pct, IntervalData intervalData) {
		this.product = product;

		bidPrice = bbo.bidPrice;
		askPrice = bbo.askPrice;
		midPrice = bbo.midPrice();
		bidSize = bbo.bidSize;
		askSize = bbo.askSize;

		bidCount1Pct = depth1Pct.bidCount;
		askCount1Pct = depth1Pct.askCount;
		bidCount5Pct = depth5Pct.bidCount;
		askCount5Pct = depth5Pct.askCount;
		bidSize1Pct = depth1Pct.bidSize;
		askSize1Pct = depth1Pct.askSize;
		bidSize5Pct = depth5Pct.bidSize;
		askSize5Pct = depth5Pct.askSize;

		lastPrice = intervalData.lastPrice;
		ret = intervalData.ret;
		volume = intervalData.volume;
		vwap = intervalData.vwap;
		bidTradeCount = intervalData.bidTradeCount;
		askTradeCount = intervalData.askTradeCount;
		bidTradeSize = intervalData.bidTradeSize;
		askTradeSize = intervalData.askTradeSize;
		newBidCount = intervalData.newBidCount;
		newAskCount = intervalData.newAskCount;
		newBidSize = intervalData.newBidSize;
		newAskSize = intervalData.newAskSize;
		bidCancelCount = intervalData.bidCancelCount;
		askCancelCount = intervalData.askCancelCount;
		bidCancelSize = intervalData.bidCancelSize;
		askCancelSize = intervalData.askCancelSize;
	}

	public ProductSnapshot(CSVRecord record) {
		product = Product.parse(record.get("product"));
		bidPrice = Double.parseDouble(record.get("bidPrice"));
		askPrice = Double.parseDouble(record.get("askPrice"));
		midPrice = Double.parseDouble(record.get("midPrice"));
		bidSize = Double.parseDouble(record.get("bidSize"));
		askSize = Double.parseDouble(record.get("askSize"));
		bidCount1Pct = Integer.parseInt(record.get("bidCount1Pct"));
		askCount1Pct = Integer.parseInt(record.get("askCount1Pct"));
		bidCount5Pct = Integer.parseInt(record.get("bidCount5Pct"));
		askCount5Pct = Integer.parseInt(record.get("askCount5Pct"));
		bidSize1Pct = Double.parseDouble(record.get("bidSize1Pct"));
		askSize1Pct = Double.parseDouble(record.get("askSize1Pct"));
		bidSize5Pct = Double.parseDouble(record.get("bidSize5Pct"));
		askSize5Pct = Double.parseDouble(record.get("askSize5Pct"));
		lastPrice = Double.parseDouble(record.get("lastPrice"));
		ret = Double.parseDouble(record.get("ret"));
		volume = Double.parseDouble(record.get("volume"));
		vwap = Double.parseDouble(record.get("vwap"));
		bidTradeCount = Long.parseLong(record.get("bidTradeCount"));
		askTradeCount = Long.parseLong(record.get("askTradeCount"));
		bidTradeSize = Double.parseDouble(record.get("bidTradeSize"));
		askTradeSize = Double.parseDouble(record.get("askTradeSize"));
		newBidCount = Long.parseLong(record.get("newBidCount"));
		newAskCount = Long.parseLong(record.get("newAskCount"));
		newBidSize = Double.parseDouble(record.get("newBidSize"));
		newAskSize = Double.parseDouble(record.get("newAskSize"));
		bidCancelCount = Long.parseLong(record.get("bidCancelCount"));
		askCancelCount = Long.parseLong(record.get("askCancelCount"));
		bidCancelSize = Double.parseDouble(record.get("bidCancelSize"));
		askCancelSize = Double.parseDouble(record.get("askCancelSize"));
	}

	/** @return Column headers for csv output */
	public static final String csvHeaderRow() {
		return "product,bidPrice,askPrice,midPrice,bidSize,askSize,bidCount1Pct,askCount1Pct,bidSize1Pct,askSize1Pct," +
			"bidCount5Pct,askCount5Pct,bidSize5Pct,askSize5Pct," +
			"lastPrice,ret,volume,vwap," +
			"bidTradeCount,askTradeCount,bidTradeSize,askTradeSize," +
			"newBidCount,newAskCount,newBidSize,newAskSize," +
			"bidCancelCount,askCancelCount,bidCancelSize,askCancelSize";
	}

	/** @return CSV output row */
	public String toCSVString() {
		final StringBuilder builder = new StringBuilder();
		builder.append(product.toString());
		builder.append(',');
		builder.append(bidPrice);
		builder.append(',');
		builder.append(askPrice);
		builder.append(',');
		builder.append(midPrice);
		builder.append(',');
		builder.append(bidSize);
		builder.append(',');
		builder.append(askSize);
		builder.append(',');
		builder.append(bidCount1Pct);
		builder.append(',');
		builder.append(askCount1Pct);
		builder.append(',');
		builder.append(bidSize1Pct);
		builder.append(',');
		builder.append(askSize1Pct);
		builder.append(',');
		builder.append(bidCount5Pct);
		builder.append(',');
		builder.append(askCount5Pct);
		builder.append(',');
		builder.append(bidSize5Pct);
		builder.append(',');
		builder.append(askSize5Pct);
		builder.append(',');
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
