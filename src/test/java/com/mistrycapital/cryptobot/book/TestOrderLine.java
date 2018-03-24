package com.mistrycapital.cryptobot.book;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.mistrycapital.cryptobot.gdax.websocket.OrderSide;

public class TestOrderLine {
	static double EPSILON = 0.00000001;

	@Test
	void shouldBuildLine() {
		OrderLineList list = new OrderLineList(true);
		assertNull(list.getNext());
		
		OrderLine line = list.findOrCreate(1.0);
		assertEquals(0, line.getCount());
		assertEquals(0.0, line.getSize(), EPSILON);
		assertNull(line.getNext());
		assertEquals(line, list.getNext());
		
		line.addOrder(2.14);
		line.addOrder(1.0);
		assertEquals(2, line.getCount());
		assertEquals(3.14, line.getSize(), EPSILON);
		assertEquals(2, list.getCountBeforePrice(Double.MAX_VALUE));
		assertEquals(2, list.getCountBeforePrice(1.0));
		assertEquals(0, list.getCountBeforePrice(0.5));
		assertEquals(3.14, list.getSizeBeforePrice(Double.MAX_VALUE));
		assertEquals(3.14, list.getSizeBeforePrice(1.0));
		assertEquals(0, list.getSizeBeforePrice(0.5));
		
		line = list.findOrCreate(2.0);
		line.addOrder(5.5);
		line = list.findOrCreate(2.00000000000001);
		line.addOrder(1.0);
		line = list.getNext();
		assertEquals(1.0, line.getPrice(), EPSILON);
		line = line.getNext();
		assertEquals(2.0, line.getPrice(), EPSILON);
		assertNull(line.getNext());
		assertEquals(2, line.getCount());
		assertEquals(6.5, line.getSize());
		assertEquals(4, list.getCountBeforePrice(Double.MAX_VALUE));
		assertEquals(9.64, list.getSizeBeforePrice(Double.MAX_VALUE), EPSILON);
		assertEquals(2, list.getCountBeforePrice(1.5));
		
		line = list.findOrCreate(1.5);
		line.addOrder(1.0);
		assertEquals(3, list.getCountBeforePrice(1.75));
		
		// verify entire line list
		line = list.getNext();
		assertEquals(1.0, line.getPrice(), EPSILON);
		assertEquals(2, line.getCount());
		assertEquals(3.14, line.getSize(), EPSILON);
		line = line.getNext();
		assertEquals(1.5, line.getPrice(), EPSILON);
		assertEquals(1, line.getCount());
		assertEquals(1.0, line.getSize(), EPSILON);
		line = line.getNext();
		assertEquals(2.0, line.getPrice(), EPSILON);
		assertEquals(2, line.getCount());
		assertEquals(6.5, line.getSize(), EPSILON);
		assertNull(line.getNext());
		assertEquals(1.0, list.getFirstPrice(), EPSILON);
		assertEquals(3.14, list.getFirstSize(), EPSILON);

		// now remove lines
		line = list.findOrCreate(1.5000000000000001);
		line.remove();
		assertNull(list.getNext().getNext().getNext());
		assertEquals(4, list.getCountBeforePrice(Double.MAX_VALUE));
		list.findOrCreate(2.0).remove(); // try one at end
		assertNull(list.getNext().getNext());
		
		// modify line
		line = list.getNext();
		line.removeOrder(1.14);
		assertEquals(1, line.getCount());
		assertEquals(2.0, line.getSize(), EPSILON);
		line.removeOrder(2.0);
		assertEquals(0, line.getCount());
		assertNull(list.getNext()); // empty list
		assertTrue(Double.isNaN(list.getFirstPrice()));
		assertTrue(Double.isNaN(list.getFirstSize()));
		
		// try clearing
		list.findOrCreate(4.0);
		list.findOrCreate(5.0);
		list.findOrCreate(6.0);
		list.clear();
		assertNull(list.getNext());
	}
	
	@Test
	void shouldBeModifiedByOrder() {
		OrderLineList list = new OrderLineList(false);
		Order order = new Order();
		order.reset(null, 1.0, 3.14, 0, OrderSide.SELL);
		OrderLine line = list.findOrCreate(1.0);
		assertEquals(0, line.getCount());
		assertEquals(0.0, line.getSize(), EPSILON);
		
		order.setLine(line);
		assertEquals(1, line.getCount());
		assertEquals(3.14, line.getSize(), EPSILON);
		assertEquals(1, list.getCountBeforePrice(0.0));
		assertEquals(3.14, list.getSizeBeforePrice(0.0), EPSILON);
		
		// modify order
		order.changeSize(2.7);
		assertEquals(2.7, line.getSize(), EPSILON);

		// also create another line to make sure it is not pointed to after removing the previous
		assertNull(line.getNext());
		list.findOrCreate(0.5);
		assertNotNull(line.getNext());
		
		order.destroy();
		assertEquals(0, line.getCount());
		assertEquals(0.0, line.getSize(), EPSILON);
		assertNull(line.getNext());
		assertEquals(0, list.getCountBeforePrice(0.0));
	}
}