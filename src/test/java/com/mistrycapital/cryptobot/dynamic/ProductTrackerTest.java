package com.mistrycapital.cryptobot.dynamic;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.mistrycapital.cryptobot.appender.FileAppender;
import com.mistrycapital.cryptobot.appender.IntervalDataAppender;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductTrackerTest {

	@Test
	void calcNextIntervalMicros() {
		fail("not yet implemented");
	}

	@Test
	void shouldTrackNewOrder() throws Exception {
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path dataDir = fs.getPath("/logs");
		Files.createDirectory(dataDir);

		FakeTimeKeeper timeKeeper = new FakeTimeKeeper(System.currentTimeMillis());
		FileAppender fileAppender = new IntervalDataAppender(dataDir, "samples", timeKeeper);
		ProductHistory history = new ProductHistory(Product.LTC_USD, fileAppender);
	}
}