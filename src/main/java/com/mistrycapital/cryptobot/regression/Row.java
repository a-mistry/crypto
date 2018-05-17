package com.mistrycapital.cryptobot.regression;

public class Row<K extends Comparable<K>> {
	private K key;
	private String[] columnNames;
	private double[] columnValues;

	Row(K key, String[] columnNames, double[] columnValues) {
		this.key = key;
		this.columnNames = columnNames;
		this.columnValues = columnValues;
	}

	public final K getKey() {
		return key;
	}

	public final String[] getColumnNames() {
		return columnNames;
	}

	public final double[] getColumnValues() {
		return columnValues;
	}

	public double getColumn(String name) {
		for(int i=0; i<columnNames.length; i++)
			if(columnNames[i].equals(name))
				return columnValues[i];
		return Double.NaN;
	}
}
