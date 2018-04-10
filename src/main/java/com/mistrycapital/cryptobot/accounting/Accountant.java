package com.mistrycapital.cryptobot.accounting;

import com.mistrycapital.cryptobot.gdax.client.Account;
import com.mistrycapital.cryptobot.gdax.common.Currency;

import java.util.List;

public class Accountant {
	private double[] available;
	private double[] balance;

	public Accountant(List<Account> accounts) {
		available = new double[Currency.count];
		balance = new double[Currency.count];
		refresh(accounts);
	}

	public void refresh(List<Account> accounts) {
		for(Account account : accounts) {
			Currency currency = account.getCurrency();
			available[currency.getIndex()] = account.getAvailable();
			balance[currency.getIndex()] = account.getBalance();
		}
	}

	public double getAvailable(Currency currency) {
		return available[currency.getIndex()];
	}

	public double getBalance(Currency currency) {
		return balance[currency.getIndex()];
	}
}
