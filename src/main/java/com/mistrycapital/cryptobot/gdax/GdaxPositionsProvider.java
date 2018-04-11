package com.mistrycapital.cryptobot.gdax;

import com.mistrycapital.cryptobot.accounting.PositionsProvider;
import com.mistrycapital.cryptobot.gdax.client.Account;
import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.time.TimeKeeper;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class GdaxPositionsProvider implements PositionsProvider {
	private final TimeKeeper timeKeeper;
	private final GdaxClient gdaxClient;
	private long lastFetchSec = -1;
	private double[] available;
	private double[] balance;

	public GdaxPositionsProvider(TimeKeeper timeKeeper, GdaxClient gdaxClient) {
		this.timeKeeper = timeKeeper;
		this.gdaxClient = gdaxClient;
	}

	private void refresh() {
		available = new double[Currency.count];
		balance = new double[Currency.count];
		try {
			List<Account> accounts = gdaxClient.getAccounts().get();
			for(Account account : accounts) {
				available[account.getCurrency().getIndex()] = account.getAvailable();
				balance[account.getCurrency().getIndex()] = account.getBalance();
			}
		} catch(ExecutionException | InterruptedException e) {
			throw new RuntimeException("Could not retrieve gdax positions", e.getCause());
		}
		lastFetchSec = timeKeeper.epochMs() / 1000L;
	}

	@Override
	public double[] getAvailable() {
		if(lastFetchSec < 0 || lastFetchSec < timeKeeper.epochMs() / 1000L)
			refresh();

		return available;
	}

	@Override
	public double[] getBalance() {
		if(lastFetchSec < 0 || lastFetchSec < timeKeeper.epochMs() / 1000L)
			refresh();

		return balance;
	}
}
