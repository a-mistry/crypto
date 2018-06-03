package com.mistrycapital.cryptobot.aggregatedata;

import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsolidatedHistoryTest {

	@Test
	void shouldGetHistory() {
		ConsolidatedSnapshot snapshot1 = mock(ConsolidatedSnapshot.class);
		ConsolidatedSnapshot snapshot2 = mock(ConsolidatedSnapshot.class);
		ConsolidatedSnapshot snapshot3 = mock(ConsolidatedSnapshot.class);
		ConsolidatedSnapshot snapshot4 = mock(ConsolidatedSnapshot.class);

		MCProperties properties = new MCProperties();
		properties.setProperty("history.secondsToKeep", "3");
		properties.setProperty("history.intervalSeconds", "1");
		Intervalizer intervalizer = new Intervalizer(properties);

		ConsolidatedHistory history = new ConsolidatedHistory(intervalizer);
		history.add(snapshot1);

		var iter = history.values().iterator();
		assertTrue(iter.hasNext());
		assertEquals(snapshot1, iter.next());
		assertNull(iter.next());
		assertFalse(iter.hasNext());

		history.add(snapshot2);
		history.add(snapshot3);
		iter = history.values().iterator();
		assertEquals(snapshot3, iter.next());
		assertEquals(snapshot2, iter.next());
		assertEquals(snapshot1, iter.next());
		assertFalse(iter.hasNext());

		history.add(snapshot4);
		iter = history.values().iterator();
		assertEquals(snapshot4, iter.next());
		assertEquals(snapshot3, iter.next());
		assertEquals(snapshot2, iter.next());
		assertFalse(iter.hasNext());
	}
}