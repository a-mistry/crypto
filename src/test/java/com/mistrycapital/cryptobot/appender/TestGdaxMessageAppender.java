package com.mistrycapital.cryptobot.appender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.gdax.websocket.Product;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;

class TestGdaxMessageAppender {
	@Test
	void shouldCalcHours() {
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520643600000L, GdaxMessageAppender.calcNextHourMillis(timeMs));
		assertEquals(1520643600000L, GdaxMessageAppender.calcNextHourMillis(timeMs + 100000L));
	}

	@Test
	void shouldGetFileName()
		throws Exception
	{
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);

		long timeInMillis = 1520640000000L; // 3/10/18 00:00:00 UTC
		FakeTimeKeeper timeKeeper = new FakeTimeKeeper(timeInMillis);
		GdaxMessageAppender appender =
			new GdaxMessageAppender(logDir, "base", ".txt", timeKeeper, new OrderBookManager(timeKeeper));
		assertEquals("base-2018-03-10-00.txt", appender.getLogFileNameForCurrentTime());
		timeKeeper.setTime(timeInMillis + 50000L);
		assertEquals("base-2018-03-10-00.txt", appender.getLogFileNameForCurrentTime());
		timeKeeper.setTime(timeInMillis + 3600000L + 50000L);
		assertEquals("base-2018-03-10-01.txt", appender.getLogFileNameForCurrentTime());
		timeKeeper.setTime(1520687563000L);
		assertEquals("base-2018-03-10-13.txt", appender.getLogFileNameForCurrentTime());
	}

	@Test
	void shouldWriteMessages()
		throws Exception
	{
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);

		TimeKeeper timeKeeper = new FakeTimeKeeper();
		GdaxMessageAppender appender =
			new GdaxMessageAppender(logDir, "base", ".txt", timeKeeper, new OrderBookManager(timeKeeper));
		Path logFile = logDir.resolve(appender.getLogFileNameForCurrentTime());
		appender.append("This is some text to write");
		appender.append("Here's some more");
		appender.close();

		List<String> lines = Files.lines(logFile).collect(Collectors.toList());
		assertEquals("This is some text to write", lines.get(0));
		assertEquals("Here's some more", lines.get(1));
		assertEquals(2, lines.size());
	}

	@Test
	void shouldRollIfNeeded()
		throws Exception
	{
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);

		OrderBookManager orderBookManager = mock(OrderBookManager.class);
		for(Product product : Product.FAST_VALUES) {
			OrderBook book = mock(OrderBook.class);
			when(book.getGdaxSnapshot()).thenReturn("Snapping " + product);
			when(orderBookManager.getBook(product)).thenReturn(book);
		}

		FakeTimeKeeper timeKeeper = new FakeTimeKeeper(1520640000000L); // 3/10/18 00:00:00 UTC
		GdaxMessageAppender appender =
			new GdaxMessageAppender(logDir, "base", ".txt", timeKeeper, orderBookManager);
		Path logFile1 = logDir.resolve(appender.getLogFileNameForCurrentTime());
		appender.append("Write some text in first file");
		assertTrue(Files.exists(logFile1));

		assertFalse(appender.rollIfNeeded());
		timeKeeper.setTime(1520640000000L + 1800000L); // 30 min forward
		assertFalse(appender.rollIfNeeded());

		timeKeeper.setTime(1520640000000L + 3600000L); // 1 hour forward
		assertTrue(appender.rollIfNeeded());
		Path logFile2 = logDir.resolve(appender.getLogFileNameForCurrentTime());
		assertNotEquals(logFile1.toAbsolutePath().toString(), logFile2.toAbsolutePath().toString());
		appender.append("Write some text in second file");
		assertTrue(Files.exists(logFile2));
		appender.close();

		List<String> lines = Files.lines(logFile2).collect(Collectors.toList());
		assertEquals(5, lines.size());
		for(int i = 0; i < 4; i++)
			assertEquals("Snapping " + Product.FAST_VALUES[i], lines.get(i));
		assertEquals("Write some text in second file", lines.get(4));
	}
}