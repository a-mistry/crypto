package com.mistrycapital.cryptobot.regression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Column<K extends Comparable<K>> implements Iterable<Cell<K>> {
	private List<Cell<K>> cells;
	private boolean isSorted;

	public Column() {
		cells = new ArrayList<>();
		isSorted = true;
	}

	public void add(K key, double value) {
		// do not sort when adding values and instead sort lazily when we iterate
		// the reason here is that most computations are likely to add a bunch of data and then iterate later
		// keeping the list sorted is considerably more expensive
		final Cell newCell = new Cell(key, value);
		if(!cells.isEmpty() && key.compareTo(cells.get(cells.size() - 1).key) < 0) {
			isSorted = false;
		}
		cells.add(newCell);
	}

	public Column<K> filter(Predicate<K> filterPredicate) {
		if(!isSorted)
			cells.sort(Comparator.comparing(c -> c.key));

		Column<K> newColumn = new Column<>();
		newColumn.cells = cells.stream()
			.filter(cell -> filterPredicate.test(cell.key))
			.collect(Collectors.toList());

		return newColumn;
	}

	@Override
	public Iterator<Cell<K>> iterator() {
		if(!isSorted)
			cells.sort(Comparator.comparing(c -> c.key));
		isSorted = true;
		return cells.iterator();
	}
}
