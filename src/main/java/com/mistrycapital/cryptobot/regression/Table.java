package com.mistrycapital.cryptobot.regression;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Table<K extends Comparable<K>> implements Iterable<Row<K>> {
	private Map<String,Column<K>> namesToColumns;
	private List<String> columnOrder;

	public Table() {
		namesToColumns = new TreeMap<>();
		columnOrder = new ArrayList<>();
	}

	public void add(String name, Column<K> column)
		throws DuplicateColumnException
	{
		if(namesToColumns.containsKey(name))
			throw new DuplicateColumnException(name + " already defined");
		namesToColumns.put(name, column);
		columnOrder.add(name);
	}

	public Column<K> column(String name) {
		return namesToColumns.get(name);
	}

	public Table<K> join(Table<K> other)
		throws DuplicateColumnException
	{
		Table<K> newTable = new Table<>();
		for(var name : columnOrder)
			newTable.add(name, namesToColumns.get(name));
		for(var name : other.columnOrder)
			newTable.add(name, other.namesToColumns.get(name));
		return newTable;
	}

	@Override
	public Iterator<Row<K>> iterator() {
		return new RowIterator();
	}

	private class RowIterator implements Iterator<Row<K>> {
		private String[] columnNames;
		private List<Iterator<Cell<K>>> columnIterators;
		private List<Cell<K>> headValues;

		RowIterator() {
			columnNames = columnOrder.toArray(new String[0]);
			columnIterators = columnOrder.stream()
				.map(namesToColumns::get)
				.map((Function<Column,Iterator<Cell<K>>>) col -> col.iterator())
				.collect(Collectors.toList());
			headValues = new ArrayList<>(columnIterators.size());
			for(int i = 0; i < columnIterators.size(); i++)
				headValues.add(null);
			advanceHeadValues();
		}

		private void advanceHeadValues() {
			for(int i = 0; i < headValues.size(); i++)
				if(headValues.get(i) == null && columnIterators.get(i).hasNext())
					headValues.set(i, columnIterators.get(i).next());
		}

		@Override
		public boolean hasNext() {
			return headValues.stream().anyMatch(Objects::nonNull);
		}

		@Override
		public Row<K> next() {
			// TODO: can save min key for a more efficient implementation

			// first first lowest key, then create a row with that key
			// assumes all columns are sorted
			Optional<Cell<K>> minKeyCellOpt = headValues.stream()
				.filter(Objects::nonNull)
				.min(Comparator.comparing(a -> a.key));
			if(!minKeyCellOpt.isPresent()) return null;
			K minKey = minKeyCellOpt.get().key;

			double[] values = new double[headValues.size()];
			for(int i = 0; i < values.length; i++) {
				Cell<K> cell = headValues.get(i);
				if(cell != null && cell.key.compareTo(minKey) == 0) {
					values[i] = cell.value;
					headValues.set(i, null);
				} else {
					values[i] = Double.NaN;
				}
			}
			advanceHeadValues();

			return new Row<>(minKey, columnNames, values);
		}
	}
}
