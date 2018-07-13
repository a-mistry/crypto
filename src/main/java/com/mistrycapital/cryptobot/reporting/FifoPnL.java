package com.mistrycapital.cryptobot.reporting;

import com.google.common.base.Joiner;
import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FifoPnL {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final double EPSILON = 1e-8;
	private static BufferedWriter csvWriter;

	public static void main(String[] args)
		throws Exception
	{
		MCProperties properties = new MCProperties();
		Path dataDir = Paths.get(properties.getProperty("dataDir"));
		log.debug("Saving message and sample data to " + dataDir);
		Path credentialsFile = Paths.get(properties.getProperty("credentialsFile"));
		log.debug("Reading credentials from " + credentialsFile);
		Properties credentials = new Properties();
		try(Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			credentials.load(reader);
		}

		DBRecorder dbRecorder = new DBRecorder(null, null, null, "mistrycapital", credentials.getProperty("MysqlUser"),
			credentials.getProperty("MysqlPassword"));

		csvWriter = Files.newBufferedWriter(dataDir.resolve("fifo.csv"), StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

		Connection con = dbRecorder.getConnection();
		PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_trades");
		ResultSet trades = statement.executeQuery();
		PreparedStatement statement2 = con.prepareStatement("SELECT * FROM crypto_hourly");
		ResultSet hourly = statement2.executeQuery();

		List<Queue<TradeInfo>> productBuys = new ArrayList<>();
		for(int i = 0; i < Product.count; i++)
			productBuys.add(new ArrayDeque<>());

		long hourMs = hourly.next() ? hourly.getTimestamp("time").getTime() : 0;
		while(trades.next()) {
			long tradeTimeMs = trades.getTimestamp("time").getTime();
			if(tradeTimeMs < hourMs) {
				// add buys to queue, pop and match on sells
				Product product = Product.parse(trades.getString("product"));
				final var buys = productBuys.get(product.getIndex());
				if("BUY".equals(trades.getString("side"))) {
					final var clientOidString = trades.getString("client_oid");
					final var clientOid = clientOidString == null ? null : UUID.fromString(clientOidString);
					buys.add(new TradeInfo(
						trades.getInt("row_id"),
						clientOid,
						tradeTimeMs,
						product,
						trades.getDouble("amount"),
						trades.getDouble("price"),
						trades.getDouble("executed_value"),
						trades.getDouble("orig_price"),
						trades.getDouble("slippage")
					));
				} else {
					final var sellPrice = trades.getDouble("price");
					final var sellOrigPrice = trades.getDouble("orig_price");
					var sellAmount = trades.getDouble("amount");
					while(sellAmount > 0 && !buys.isEmpty()) {
						final var matchBuy = buys.peek();
						final var matchAmount = Math.min(matchBuy.remainingAmount, sellAmount);
						matchBuy.remainingAmount -= matchAmount;
						sellAmount -= matchAmount;
						matchBuy.crossedValue += matchAmount * sellPrice;
						matchBuy.crossedOrigValue += matchAmount * sellOrigPrice;
						if(matchBuy.remainingAmount < EPSILON) {
							buys.remove();
							final var avgSellPrice = matchBuy.crossedValue / matchBuy.amount;
							final var avgSellOrigPrice = matchBuy.crossedOrigValue / matchBuy.amount;
							logMatch(matchBuy.rowId,
								matchBuy.clientOid,
								matchBuy.timeMillis,
								product,
								matchBuy.amount,
								matchBuy.price,
								avgSellPrice,
								matchBuy.origPrice,
								avgSellOrigPrice
							);
						}
					}
					if(sellAmount > 0)
						log.info("Unmatched sell " + new Date(tradeTimeMs) + " " + sellAmount + " " + product + " @ " +
							sellPrice);
				}
			}

			// reset all position queues here on zero position
			if(tradeTimeMs > hourMs && hourly.next()) {
				hourMs = hourly.getTimestamp("time").getTime();
				if(hourly.getDouble("usd") == hourly.getDouble("position_usd")) {
					for(final var buys : productBuys) {
						while(!buys.isEmpty()) {
							final var tradeInfo = buys.remove();
							log.info("Removing purchase " + tradeInfo + " due to zero position");
						}
					}
				}
			}
		}

		statement.close();
		statement2.close();
		con.close();

		csvWriter.close();
	}

	private static boolean logMatchFirstCalled = true;

	private static void logMatch(int rowId, UUID clientOid, long timeMillis, Product product, double amount,
		double buyPrice, double sellPrice, double buyOrigPrice, double sellOrigPrice)
		throws IOException
	{
		if(logMatchFirstCalled) {
			logMatchFirstCalled = false;
			csvWriter.append("rowId,clientOid,date,time,unixTimestamp,product,amount," +
				"buyPrice,sellPrice,buyOrigPrice,sellOrigPrice,PnL,expectedPnL\n");
		}
		String clientOidString = clientOid == null ? "" : clientOid.toString();
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneOffset.UTC);
		csvWriter.append(Joiner.on(',').join(new Object[] {
			rowId,
			clientOidString,
			dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
			dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME),
			timeMillis / 1000L,
			product,
			amount,
			buyPrice,
			sellPrice,
			buyOrigPrice,
			sellOrigPrice,
			(sellPrice - buyPrice) * amount,
			(sellOrigPrice - buyOrigPrice) * amount
		}));
		csvWriter.append('\n');
	}

	private static class TradeInfo {
		final int rowId;
		final UUID clientOid;
		final long timeMillis;
		final Product product;
		final double amount;
		final double price;
		final double executedValue;
		final double origPrice;
		final double slippage;
		double remainingAmount;
		double crossedValue;
		double crossedOrigValue;

		public TradeInfo(int rowId, UUID clientOid, long timeMillis, Product product, double amount, double price,
			double executedValue, double origPrice, double slippage)
		{
			this.rowId = rowId;
			this.clientOid = clientOid;
			this.timeMillis = timeMillis;
			this.product = product;
			this.amount = amount;
			this.price = price;
			this.executedValue = executedValue;
			this.origPrice = origPrice;
			this.slippage = slippage;
			remainingAmount = amount;
		}

		@Override
		public String toString() {
			return amount + " " + product + " @ " + price;
		}
	}
}
