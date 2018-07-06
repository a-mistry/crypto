package com.mistrycapital.cryptobot.regression;

import org.apache.commons.math3.stat.regression.MillerUpdatingRegression;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.UpdatingMultipleLinearRegression;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

	public RegressionResults regress(String yCol, String[] xCols)
		throws ColumnNotFoundException
	{
		final UpdatingMultipleLinearRegression regression = new MillerUpdatingRegression(xCols.length, true);
		final int yColNo = columnOrder.indexOf(yCol);
		if(yColNo < 0) throw new ColumnNotFoundException("No column named " + yCol);
		final int[] xColNos = new int[xCols.length];
		for(int i = 0; i < xCols.length; i++) {
			xColNos[i] = columnOrder.indexOf(xCols[i]);
			if(xColNos[i] < 0) throw new ColumnNotFoundException("No column named " + xCols[i]);
		}
		for(final Row<K> row : this) {
			final double[] values = row.getColumnValues();
			final double y = values[yColNo];
			final double[] x = new double[xCols.length];
			boolean hasNaN = Double.isNaN(y);
			if(!hasNaN)
				for(int i = 0; i < xCols.length; i++) {
					x[i] = values[xColNos[i]];
					if(Double.isNaN(x[i])) {
						hasNaN = true;
						break;
					}
				}
			if(!hasNaN)
				regression.addObservation(x, y);
		}
		return regression.regress();
	}

	public Table<K> filter(Predicate<K> filterPredicate) {
		try {
			Table<K> newTable = new Table<>();
			for(var name : columnOrder)
				newTable.add(name, namesToColumns.get(name).filter(filterPredicate));
			return newTable;
		} catch(DuplicateColumnException e) {
			throw new RuntimeException(e);
		}
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
			for(int i = 0; i < headValues.size(); i++)
				if(headValues.get(i) != null)
					return true;
			return false;
		}

		@Override
		public Row<K> next() {
			// TODO: can save min key for a more efficient implementation

			// first first lowest key, then create a row with that key
			// assumes all columns are sorted
			K minKey = null;
			for(int i = 0; i < headValues.size(); i++) {
				Cell<K> headValue = headValues.get(i);
				if(headValue != null && (minKey == null || headValue.key.compareTo(minKey) < 0)) {
					minKey = headValue.key;
				}
			}
			if(minKey == null) return null;

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
