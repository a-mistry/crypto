package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.dynamic.DynamicTracker;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.dynamic.ProductHistory;
import com.mistrycapital.cryptobot.gdax.websocket.Product;

public class ConsolidatedData {
	private final BBO[] bbo;
	private final Depth[][] depths;
	private final ProductHistory[] histories;

	private ConsolidatedData() {
		bbo = new BBO[Product.count];
		depths = new Depth[Product.count][];
		histories = new ProductHistory[Product.count];
		for(Product product : Product.FAST_VALUES) {
			final int index = product.getIndex();
			bbo[index] = new BBO();
			depths[index] = new Depth[2];
			depths[index][0] = new Depth();
			depths[index][0].pctFromMid = 0.01;
			depths[index][1] = new Depth();
			depths[index][1].pctFromMid = 0.05;
		}
	}

	public static ConsolidatedData getSnapshot(final OrderBookManager orderBookManager,
		final DynamicTracker dynamicTracker)
	{
		ConsolidatedData data = new ConsolidatedData();
		for(Product product : Product.FAST_VALUES) {
			final int index = product.getIndex();
			data.histories[index] = dynamicTracker.getProductHistory(product);
			orderBookManager.getBook(product).recordDepthsAndBBO(data.bbo[index], data.depths[index]);
		}
		return data;
	}

	public BBO getBBO(Product product) {
		return bbo[product.getIndex()];
	}

	public Depth getDepth1Pct(Product product) {
		return depths[product.getIndex()][0];
	}

	public Depth getDepth5Pct(Product product) {
		return depths[product.getIndex()][1];
	}

	public IntervalData getLatestInterval(Product product) {
		return histories[product.getIndex()].latest();
	}

	public ProductHistory getHistory(Product product) {
		return histories[product.getIndex()];
	}

	public String toCSVString(Product product) {
		final int index = product.getIndex();
		final StringBuilder builder = new StringBuilder();
		final BBO bbo = this.bbo[index];
		builder.append(bbo.bidPrice);
		builder.append(',');
		builder.append(bbo.askPrice);
		builder.append(',');
		builder.append(bbo.midPrice());
		builder.append(',');
		builder.append(bbo.bidSize);
		builder.append(',');
		builder.append(bbo.askSize);
		builder.append(',');
		for(int i=0; i<2; i++) {
			final Depth depth = depths[index][i];
			builder.append(depth.bidCount);
			builder.append(',');
			builder.append(depth.askCount);
			builder.append(',');
			builder.append(depth.bidSize);
			builder.append(',');
			builder.append(depth.askSize);
			builder.append(',');
		}
		builder.append(histories[index].latest().toCSVString());
		return builder.toString();
	}

	public static String csvHeaderRow() {
		return "bid,ask,mid,bid_size,ask_size,bid_count_1pct,ask_count_1pct,bid_size_1pct,ask_size_1pct," +
			"bid_count_5pct,ask_count_5pct,bid_size_1pct,ask_size_1pct," + IntervalData.csvHeaderRow();
	}
}