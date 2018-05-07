package com.mistrycapital.cryptobot.database;

import java.sql.*;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.slf4j.Logger;

/** Records trade and position info to the database */
public class DBRecorder {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Accountant accountant;
	private final OrderBookManager orderBookManager;
	private final MysqlDataSource dataSource;

	public DBRecorder(Accountant accountant, OrderBookManager orderBookManager, String user, String password) {
		this.accountant = accountant;
		this.orderBookManager = orderBookManager;
		try {
			dataSource = new MysqlDataSource();
			dataSource.setServerName("mistry.info");
			dataSource.setPort(3306);
			dataSource.setDatabaseName("mistrycapital");
			dataSource.setUser(user);
			dataSource.setPassword(password);
			dataSource.setRequireSSL(true);
		} catch(SQLException e) {
			log.error("Error loading database recorder", e);
			throw new RuntimeException(e);
		}
	}

	/** Records current positions and prices to the database */
	public void recordPositions() {
		try {
			Connection con = dataSource.getConnection();
/*			PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_positions");
			ResultSet results = statement.executeQuery();
			while(results.next()) {
				System.err.println(results);
			}*/
			con.close();
		} catch(SQLException e) {
			log.error("Could not save positions to database", e);
		}
	}

	/**
	 * Records trade to the database. Includes canceled/not done orders
	 */
	public void recordTrade(OrderInfo orderInfo) {
		try {
			Connection con = dataSource.getConnection();
/*			PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_positions");
			ResultSet results = statement.executeQuery();
			while(results.next()) {
				System.err.println(results);
			}*/
			con.close();
		} catch(SQLException e) {
			log.error("Could not record trade to database: " + orderInfo, e);
		}
	}
}
