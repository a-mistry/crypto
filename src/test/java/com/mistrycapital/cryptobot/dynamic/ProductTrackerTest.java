package com.mistrycapital.cryptobot.dynamic;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.gdax.websocket.Done;
import com.mistrycapital.cryptobot.gdax.websocket.Match;
import com.mistrycapital.cryptobot.gdax.websocket.Open;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductTrackerTest {
	private Open openBid;
	private Open openAsk;
	private Done canceledAsk;
	private Done canceledBid1;
	private Done canceledBid2;
	private Match matchBid;
	private Match matchAsk1;
	private Match matchAsk2;

	@BeforeEach
	void setUp() {
		JsonObject msgJson = new JsonObject();
		msgJson.addProperty("side", "buy");
		msgJson.addProperty("product_id", "BTC-USD");
		msgJson.addProperty("time", "2014-11-07T08:19:27.028459Z");
		msgJson.addProperty("sequence", 10L);
		msgJson.addProperty("order_id", UUID.randomUUID().toString());
		msgJson.addProperty("price", 20.0);
		msgJson.addProperty("remaining_size", 1.0);
		openBid = new Open(msgJson);

		msgJson.addProperty("side", "sell");
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 2.0);
		openAsk = new Open(msgJson);

		msgJson.addProperty("side", "sell");
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("remaining_size", 3.0);
		msgJson.addProperty("reason", "canceled");
		canceledAsk = new Done(msgJson);
		msgJson.addProperty("side", "buy");
		msgJson.addProperty("remaining_size", 13.0);
		canceledBid1 = new Done(msgJson);
		msgJson.addProperty("remaining_size", 2.0);
		canceledBid2 = new Done(msgJson);

		msgJson.addProperty("side", "buy");
		msgJson.addProperty("price", 22.0);
		msgJson.addProperty("trade_id", 10L);
		msgJson.addProperty("maker_order_id", "ac928c66-ca53-498f-9c13-a110027a60e8");
		msgJson.addProperty("taker_order_id", "132fb6ae-456b-4654-b4e0-d681ac05cea1");
		msgJson.addProperty("size", 3.14);
		matchBid = new Match(msgJson);
		msgJson.addProperty("side", "sell");
		msgJson.addProperty("price", 30.0);
		msgJson.addProperty("size", 4.0);
		matchAsk1 = new Match(msgJson);
		msgJson.addProperty("price", 29.0);
		msgJson.addProperty("size", 1.0);
		matchAsk2 = new Match(msgJson);
	}


	@Test
	void shouldTrackNewOrder()
		throws Exception
	{
		ProductHistory history = new ProductHistory(100);
		ProductTracker tracker = new ProductTracker();

		// first do new/canceled orders
		tracker.process(openBid);
		tracker.process(openAsk);
		tracker.process(canceledAsk);
		tracker.process(canceledBid1);
		tracker.process(canceledBid2);

		IntervalData intervalData = tracker.snapshot();
		assertEquals(Double.NaN, intervalData.lastPrice);
		assertEquals(Double.NaN, intervalData.ret);
		assertEquals(0, intervalData.volume);
		assertEquals(Double.NaN, intervalData.vwap);
		assertEquals(0, intervalData.bidTradeCount);
		assertEquals(0.0, intervalData.bidTradeSize);
		assertEquals(0, intervalData.askTradeCount);
		assertEquals(0.0, intervalData.askTradeSize);
		assertEquals(2, intervalData.bidCancelCount);
		assertEquals(canceledBid1.getRemainingSize() + canceledBid2.getRemainingSize(), intervalData.bidCancelSize);
		assertEquals(1, intervalData.askCancelCount);
		assertEquals(canceledAsk.getRemainingSize(), intervalData.askCancelSize);
		assertEquals(1, intervalData.newBidCount);
		assertEquals(openBid.getRemainingSize(), intervalData.newBidSize);
		assertEquals(1, intervalData.newAskCount);
		assertEquals(openAsk.getRemainingSize(), intervalData.newAskSize);

		// now verify trades
		tracker.process(matchBid);
		tracker.snapshot();
		tracker.process(matchBid);
		tracker.process(matchAsk1);
		tracker.process(matchAsk2);

		intervalData = tracker.snapshot();
		assertEquals(matchAsk2.getPrice(), intervalData.lastPrice);
		assertEquals(matchAsk2.getPrice() / matchBid.getPrice() - 1, intervalData.ret);
		List<Match> matchList = Arrays.asList(matchBid, matchAsk1, matchAsk2);
		double volume = matchList.stream().mapToDouble(Match::getSize).sum();
		double vwap = matchList.stream().mapToDouble(match -> match.getPrice() * match.getSize()).sum() / volume;
		assertEquals(volume, intervalData.volume);
		assertEquals(vwap, intervalData.vwap);
		assertEquals(1, intervalData.bidTradeCount);
		assertEquals(matchBid.getSize(), intervalData.bidTradeSize);
		assertEquals(2, intervalData.askTradeCount);
		assertEquals(matchAsk1.getSize() + matchAsk2.getSize(), intervalData.askTradeSize);
		assertEquals(0, intervalData.bidCancelCount);
		assertEquals(0.0, intervalData.bidCancelSize);
		assertEquals(0, intervalData.askCancelCount);
		assertEquals(0.0, intervalData.askCancelSize);
		assertEquals(0, intervalData.newBidCount);
		assertEquals(0.0, intervalData.newBidSize);
		assertEquals(0, intervalData.newAskCount);
		assertEquals(0.0, intervalData.newAskSize);
	}
}