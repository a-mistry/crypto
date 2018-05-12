package com.mistrycapital.cryptobot.regression;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Column<K extends Comparable<K>> implements Iterable<Cell<K>> {
	private List<Cell<K>> cells;

	public Column() {
		cells = new ArrayList<>();
	}

	public void add(K key, double value) {
		final Cell newCell = new Cell(key, value);
		if(cells.isEmpty() || key.compareTo(cells.get(cells.size() - 1).key) > 0) {
			cells.add(newCell);
		} else {
			// TODO: improve this with binary search, for now it's just simple insertion
			for(int i = 0; i < cells.size(); i++)
				if(key.compareTo(cells.get(i).key) <= 0) {
					cells.add(i, newCell);
				}
		}
	}

	@Override
	public Iterator<Cell<K>> iterator() {
		return cells.iterator();
	}
}
