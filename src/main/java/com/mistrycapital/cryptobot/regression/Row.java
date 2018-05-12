package com.mistrycapital.cryptobot.regression;

public class Row<K extends Comparable<K>> {
	private K key;
	private double[] columnValues;
	private String[] columnNames;

	Row(K key, String[] columnNames, double[] columnValues) {
		this.key = key;
		this.columnNames = columnNames;
		this.columnValues = columnValues;
	}
}
