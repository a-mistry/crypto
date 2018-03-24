package com.mistrycapital.cryptobot.appender;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.time.FakeTimeKeeper;
import com.mistrycapital.cryptobot.time.TimeKeeper;

public class TestGdaxMessageAppender {
	@Test
	void shouldCalcHours() {
		long timeMs = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals(1520643600000L, GdaxMessageAppender.calcNextHourMillis(timeMs));
		assertEquals(1520643600000L, GdaxMessageAppender.calcNextHourMillis(timeMs+100000L));
	}
	
	@Test
	void shouldGetFileName() throws Exception {
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);
		
		TimeKeeper timeKeeper = new FakeTimeKeeper(Instant.now());
		GdaxMessageAppender appender = GdaxMessageAppender.create(logDir, "base", ".txt", new OrderBookManager(timeKeeper));
		long timeInMillis = 1520640000000L; // 3/10/18 00:00:00 UTC
		assertEquals("base-2018-03-10-00.txt", appender.getLogFileName(timeInMillis));
		assertEquals("base-2018-03-10-00.txt", appender.getLogFileName(timeInMillis+50000L));
		assertEquals("base-2018-03-10-01.txt", appender.getLogFileName(timeInMillis+3600000L+50000L));
		assertEquals("base-2018-03-10-13.txt", appender.getLogFileName(1520687563000L));
	}

	@Test
	void shouldWriteMessages() throws Exception {
		FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
		Path logDir = fs.getPath("/logs");
		Files.createDirectory(logDir);
		
		TimeKeeper timeKeeper = new FakeTimeKeeper(Instant.now());
		GdaxMessageAppender appender = GdaxMessageAppender.create(logDir, "base", ".txt", new OrderBookManager(timeKeeper));
		Path logFile = logDir.resolve(appender.getLogFileName(System.currentTimeMillis()));
		appender.append("This is some text to write");
		appender.append("Here's some more");
		appender.close();
		
		List<String> lines = Files.lines(logFile).collect(Collectors.toList());
		assertEquals("This is some text to write", lines.get(0));
		assertEquals("Here's some more", lines.get(1));
		assertEquals(2, lines.size());
	}
	
	@Test
	void shouldRollIfNeeded() throws Exception {
		fail("not yet implemented, need timekeeper");
	}
}