package com.mistrycapital.cryptobot.sim;

import org.junit.jupiter.api.Test;

import java.util.zip.ZipEntry;

import static com.mistrycapital.cryptobot.sim.GdaxMessageFileReader.hourlyJsonSorter;
import static org.junit.jupiter.api.Assertions.*;

class GdaxMessageFileReaderTest {

	@Test
	public void shouldCompareJsonFiles() {
		ZipEntry ZipEntry1 = new ZipEntry("gdax-orders-2018-01-02-00.json");
		ZipEntry ZipEntry2 = new ZipEntry("gdax-orders-2018-01-02-01.json");
		ZipEntry ZipEntry3 = new ZipEntry("gdax-orders-2018-01-02-05.json");
		ZipEntry ZipEntry4 = new ZipEntry("gdax-orders-2018-01-02-10.json");
		assertEquals(-1, hourlyJsonSorter.compare(ZipEntry1, ZipEntry2));
		assertEquals(-1, hourlyJsonSorter.compare(ZipEntry2, ZipEntry3));
		assertEquals(-1, hourlyJsonSorter.compare(ZipEntry3, ZipEntry4));
		assertEquals(0, hourlyJsonSorter.compare(ZipEntry1, ZipEntry1));
		assertEquals(1, hourlyJsonSorter.compare(ZipEntry2, ZipEntry1));
		assertEquals(1, hourlyJsonSorter.compare(ZipEntry3, ZipEntry1));
	}
}