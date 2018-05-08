package com.mistrycapital.cryptobot.database;

import java.sql.*;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.slf4j.Logger;

/** Records trade and position info to the database */
public class DBRecorder {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final OrderBookManager orderBookManager;
	private final MysqlDataSource dataSource;

	public DBRecorder(TimeKeeper timeKeeper, Accountant accountant, OrderBookManager orderBookManager,
		String databaseName, String user, String password)
	{
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
		try {
			dataSource = new MysqlDataSource();
			dataSource.setServerName("mistry.info");
			dataSource.setPort(3306);
			dataSource.setDatabaseName(databaseName);
			dataSource.setUser(user);
			dataSource.setPassword(password);
			dataSource.setRequireSSL(true);
			dataSource.setServerTimezone("UTC");
		} catch(SQLException e) {
			log.error("Error loading database recorder", e);
			throw new RuntimeException(e);
		}
	}

	/** Records current positions and prices to the database */
	public void recordPositions() {
		try {
			Connection con = dataSource.getConnection();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_positions"
				+ " (time,currency,position,price) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE"
				+ " position=VALUES(position),price=VALUES(price)");
			statement.setTimestamp(1, new Timestamp(timeKeeper.epochMs()));
			for(Currency currency : Currency.FAST_VALUES) {
				statement.setString(2, currency.toString());
				statement.setDouble(3, accountant.getBalance(currency));
				if(currency.isCrypto()) {
					Product product = currency.getUsdProduct();
					final double price = orderBookManager.getBook(product).getBBO().midPrice();
					statement.setDouble(4, price);
				} else {
					statement.setNull(4, Types.DOUBLE);
				}
				statement.executeUpdate();
			}
			statement.close();
			con.close();
		} catch(Exception e) {
			log.error("Could not save positions to database", e);
		}
	}

	/**
	 * Records trade to the database. Includes canceled/not done orders
	 */
	public void recordTrade(OrderInfo orderInfo) {
		try {
			Connection con = dataSource.getConnection();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_trades"
				+ " (time,product,side,amount,price,executed_value,fees_usd) VALUES (?,?,?,?,?,?,?)");
			Timestamp timestamp = new Timestamp(orderInfo.getTimeMicros() / 1000L);
			timestamp.setNanos((int) (orderInfo.getTimeMicros() % 1000L) * 1000);
			statement.setTimestamp(1, timestamp);
			statement.setString(2, orderInfo.getProduct().toString());
			statement.setString(3, orderInfo.getOrderSide().toString());
			statement.setDouble(4, orderInfo.getFilledSize());
			statement.setDouble(5, orderInfo.getPrice());
			statement.setDouble(6, orderInfo.getExecutedValue());
			statement.setDouble(7, orderInfo.getFillFees());
			statement.executeUpdate();
			statement.close();
			con.close();
		} catch(Exception e) {
			log.error("Could not record trade to database: " + orderInfo, e);
		}
	}
}
