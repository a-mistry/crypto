package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.WeightedMid;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.gdax.common.Product;
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

	/** Weighted mid using top level */
	public final double weightedMid1;
	/** Weighted mid using top 5 levels */
	public final double weightedMid5;
	/** Weighted mid using top 10 levels */
	public final double weightedMid10;
	/** Weighted mid using top 20 levels */
	public final double weightedMid20;
	/** Weighted mid using top 50 levels */
	public final double weightedMid50;
	/** Weighted mid using top 100 levels */
	public final double weightedMid100;

	/** Size of top level */
	public final double size1Level;
	/** Size of top 5 levels */
	public final double size5Level;
	/** Size of top 10 levels */
	public final double size10Level;
	/** Size of top 20 levels */
	public final double size20Level;
	/** Size of top 50 levels */
	public final double size50Level;
	/** Size of top 100 levels */
	public final double size100Level;

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
		WeightedMid[] mids = new WeightedMid[6];
		for(int i=0; i<mids.length; i++)
			mids[i] = new WeightedMid();
		mids[0].numLevels = 1;
		mids[1].numLevels = 5;
		mids[2].numLevels = 10;
		mids[3].numLevels = 20;
		mids[4].numLevels = 50;
		mids[5].numLevels = 100;
		orderBook.recordDepthsAndBBO(bbo, depths, mids);

		return new ProductSnapshot(product, bbo, depths[0], depths[1], mids, intervalData);
	}

	private ProductSnapshot(Product product, BBO bbo, Depth depth1Pct, Depth depth5Pct, WeightedMid[] mids, IntervalData intervalData) {
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

		weightedMid1 = mids[0].weightedMidPrice;
		weightedMid5 = mids[1].weightedMidPrice;
		weightedMid10 = mids[2].weightedMidPrice;
		weightedMid20 = mids[3].weightedMidPrice;
		weightedMid50 = mids[4].weightedMidPrice;
		weightedMid100 = mids[5].weightedMidPrice;

		size1Level = mids[0].size;
		size5Level = mids[1].size;
		size10Level = mids[2].size;
		size20Level = mids[3].size;
		size50Level = mids[4].size;
		size100Level = mids[5].size;

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
		weightedMid1 = Double.parseDouble(record.get("weightedMid1"));
		weightedMid5 = Double.parseDouble(record.get("weightedMid5"));
		weightedMid10 = Double.parseDouble(record.get("weightedMid10"));
		weightedMid20 = Double.parseDouble(record.get("weightedMid20"));
		weightedMid50 = Double.parseDouble(record.get("weightedMid50"));
		weightedMid100 = Double.parseDouble(record.get("weightedMid100"));
		size1Level = Double.parseDouble(record.get("size1Level"));
		size5Level = Double.parseDouble(record.get("size5Level"));
		size10Level = Double.parseDouble(record.get("size10Level"));
		size20Level = Double.parseDouble(record.get("size20Level"));
		size50Level = Double.parseDouble(record.get("size50Level"));
		size100Level = Double.parseDouble(record.get("size100Level"));
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
			"weightedMid1,weightedMid5,weightedMid10,weightedMid20,weightedMid50,weightedMid100," +
			"size1Level,size5Level,size10Level,size20Level,size50Level,size100Level," +
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
		builder.append(weightedMid1);
		builder.append(',');
		builder.append(weightedMid5);
		builder.append(',');
		builder.append(weightedMid10);
		builder.append(',');
		builder.append(weightedMid20);
		builder.append(',');
		builder.append(weightedMid50);
		builder.append(',');
		builder.append(weightedMid100);
		builder.append(',');
		builder.append(size1Level);
		builder.append(',');
		builder.append(size5Level);
		builder.append(',');
		builder.append(size10Level);
		builder.append(',');
		builder.append(size20Level);
		builder.append(',');
		builder.append(size50Level);
		builder.append(',');
		builder.append(size100Level);
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
