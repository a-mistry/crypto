package com.mistrycapital.cryptobot.gdax.client;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.common.Currency;

import java.util.UUID;

public class Account {
	/** Account id */
	private final UUID id;
	/** Currency account is denominated in */
	private final Currency currency;
	/** Total funds in the account */
	private final double balance;
	/** Funds available to withdraw or trade */
	private final double available;
	/** Funds on hold */
	private final double hold;

	Account(JsonObject json) {
		id = UUID.fromString(json.get("id").getAsString());
		currency = Currency.valueOf(json.get("currency").getAsString());
		balance = Double.parseDouble(json.get("balance").getAsString());
		available = Double.parseDouble(json.get("available").getAsString());
		hold = Double.parseDouble(json.get("hold").getAsString());
	}

	/** @return Account id */
	public UUID getId() {
		return id;
	}

	/** @return Currency account is denominated in */
	public Currency getCurrency() {
		return currency;
	}

	/** @return Total funds in the account */
	public double getBalance() {
		return balance;
	}

	/** @return Funds available to withdraw or trade */
	public double getAvailable() {
		return available;
	}

	/** @return Funds on hold */
	public double getHold() {
		return hold;
	}
}