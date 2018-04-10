package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Depth;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.dynamic.IntervalData;
import com.mistrycapital.cryptobot.gdax.common.Product;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ProductSnapshotTest {
	private static final double EPSILON = 0.00000001;

	@Test
	void shouldCopySnapshotData() {
		OrderBook orderBook = mock(OrderBook.class);
		doAnswer(invocationOnMock -> {
			BBO bbo = invocationOnMock.getArgument(0);
			Depth[] depths = invocationOnMock.getArgument(1);
			bbo.bidPrice = 90.0;
			bbo.bidSize = 1.0;
			bbo.askPrice = 91.0;
			bbo.askSize = 3.14;
			depths[0].bidCount = 4;
			depths[0].bidSize = 2.0;
			depths[0].askCount = 5;
			depths[0].askSize = 4.14;
			depths[1].bidCount = 6;
			depths[1].bidSize = 3.0;
			depths[1].askCount = 7;
			depths[1].askSize = 5.14;
			return null;
		}).when(orderBook).recordDepthsAndBBO(any(), any());
		IntervalData intervalData = new IntervalData();
		intervalData.lastPrice = 45.0;
		intervalData.ret = 0.007;
		intervalData.volume = 15.4;
		intervalData.vwap = 25.4;
		intervalData.bidTradeCount = 12;
		intervalData.bidTradeSize = 7.5;
		intervalData.askTradeCount = 13;
		intervalData.askTradeSize = 8.5;
		intervalData.newBidCount = 80;
		intervalData.newBidSize = 20.25;
		intervalData.newAskCount = 90;
		intervalData.newAskSize = 21.25;
		intervalData.bidCancelCount = 120;
		intervalData.bidCancelSize = 50.02;
		intervalData.askCancelCount = 122;
		intervalData.askCancelSize = 55.52;

		ProductSnapshot snapshot = ProductSnapshot.getSnapshot(Product.BTC_USD, orderBook, intervalData);
		assertEquals(90.0, snapshot.bidPrice, EPSILON);
		assertEquals(1.0, snapshot.bidSize, EPSILON);
		assertEquals(91.0, snapshot.askPrice, EPSILON);
		assertEquals(3.14, snapshot.askSize, EPSILON);
		assertEquals(90.5, snapshot.midPrice, EPSILON);
		assertEquals(4, snapshot.bidCount1Pct);
		assertEquals(2.0, snapshot.bidSize1Pct, EPSILON);
		assertEquals(5, snapshot.askCount1Pct);
		assertEquals(4.14, snapshot.askSize1Pct, EPSILON);
		assertEquals(6, snapshot.bidCount5Pct);
		assertEquals(3.0, snapshot.bidSize5Pct, EPSILON);
		assertEquals(7, snapshot.askCount5Pct);
		assertEquals(5.14, snapshot.askSize5Pct, EPSILON);
		assertEquals(45.0, snapshot.lastPrice, EPSILON);
		assertEquals(0.007, snapshot.ret, EPSILON);
		assertEquals(15.4, snapshot.volume, EPSILON);
		assertEquals(25.4, snapshot.vwap, EPSILON);
		assertEquals(12, snapshot.bidTradeCount);
		assertEquals(7.5, snapshot.bidTradeSize, EPSILON);
		assertEquals(13, snapshot.askTradeCount);
		assertEquals(8.5, snapshot.askTradeSize, EPSILON);
		assertEquals(80, snapshot.newBidCount);
		assertEquals(20.25, snapshot.newBidSize, EPSILON);
		assertEquals(90, snapshot.newAskCount);
		assertEquals(21.25, snapshot.newAskSize, EPSILON);
		assertEquals(120, snapshot.bidCancelCount);
		assertEquals(50.02, snapshot.bidCancelSize, EPSILON);
		assertEquals(122, snapshot.askCancelCount);
		assertEquals(55.52, snapshot.askCancelSize, EPSILON);

		List<Object> allData = Arrays.asList(
			snapshot.product,
			snapshot.bidPrice,
			snapshot.askPrice,
			snapshot.midPrice,
			snapshot.bidSize,
			snapshot.askSize,
			snapshot.bidCount1Pct,
			snapshot.askCount1Pct,
			snapshot.bidSize1Pct,
			snapshot.askSize1Pct,
			snapshot.bidCount5Pct,
			snapshot.askCount5Pct,
			snapshot.bidSize5Pct,
			snapshot.askSize5Pct,
			snapshot.lastPrice,
			snapshot.ret,
			snapshot.volume,
			snapshot.vwap,
			snapshot.bidTradeCount,
			snapshot.askTradeCount,
			snapshot.bidTradeSize,
			snapshot.askTradeSize,
			snapshot.newBidCount,
			snapshot.newAskCount,
			snapshot.newBidSize,
			snapshot.newAskSize,
			snapshot.bidCancelCount,
			snapshot.askCancelCount,
			snapshot.bidCancelSize,
			snapshot.askCancelSize
		);
		String joined = allData.stream().map(Object::toString).collect(Collectors.joining(","));
		assertEquals(joined, snapshot.toCSVString());
	}
}