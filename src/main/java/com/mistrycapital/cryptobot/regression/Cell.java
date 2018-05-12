package com.mistrycapital.cryptobot.regression;

public class Cell<K> {
	public final K key;
	public final double value;

	Cell(K key, double value) {
		this.key = key;
		this.value = value;
	}
}
