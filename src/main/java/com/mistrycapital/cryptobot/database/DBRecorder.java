package com.mistrycapital.cryptobot.database;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.Done;
import com.mistrycapital.cryptobot.gdax.websocket.Match;
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
			dataSource.setUseSSL(true);
			dataSource.setServerTimezone("UTC");
			dataSource.setVerifyServerCertificate(false);
		} catch(SQLException e) {
			log.error("Error loading database recorder", e);
			throw new RuntimeException(e);
		}
	}

	/** Records current positions and prices to the database (done daily) */
	public void recordDailyPositions() {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_positions"
				+ " (time,currency,position,price) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE"
				+ " position=VALUES(position),price=VALUES(price)");
			statement.setTimestamp(1, new Timestamp(timeKeeper.epochMs()));
			for(Currency currency : Currency.FAST_VALUES) {
				statement.setString(2, currency.toString());
				statement.setDouble(3, accountant.getBalance(currency));
				double price = Double.NaN;
				if(currency.isCrypto()) {
					Product product = currency.getUsdProduct();
					price = orderBookManager.getBook(product).getBBO().midPrice();
				}
				if(Double.isNaN(price)) {
					// either not a crypto or building book and hence don't have data
					statement.setNull(4, Types.DOUBLE);
				} else {
					statement.setDouble(4, price);
				}
				statement.executeUpdate();
			}
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not save positions to database", e);
		}
	}

	/** Records current positions and prices to the database (done hourly) */
	public void recordHourlyPositions() {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement(
				"INSERT INTO crypto_hourly "
					+ " (time,btc,bch,eth,ltc,usd,eur,gbp,price_btc,price_bch,price_eth,price_ltc,position_usd)"
					+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
			);
			final double btc = accountant.getBalance(Currency.BTC);
			final double bch = accountant.getBalance(Currency.BCH);
			final double eth = accountant.getBalance(Currency.ETH);
			final double ltc = accountant.getBalance(Currency.LTC);
			final double usd = accountant.getBalance(Currency.USD);
			final double priceBtc = orderBookManager.getBook(Product.BTC_USD).getBBO().midPrice();
			final double priceBch = orderBookManager.getBook(Product.BCH_USD).getBBO().midPrice();
			final double priceEth = orderBookManager.getBook(Product.ETH_USD).getBBO().midPrice();
			final double priceLtc = orderBookManager.getBook(Product.LTC_USD).getBBO().midPrice();
			statement.setTimestamp(1, new Timestamp((timeKeeper.epochMs() / 1000) * 1000));
			statement.setDouble(2, btc);
			statement.setDouble(3, bch);
			statement.setDouble(4, eth);
			statement.setDouble(5, ltc);
			statement.setDouble(6, usd);
			statement.setDouble(7, accountant.getBalance(Currency.EUR));
			statement.setDouble(8, accountant.getBalance(Currency.GBP));
			statement.setDouble(9, priceBtc);
			statement.setDouble(10, priceBch);
			statement.setDouble(11, priceEth);
			statement.setDouble(12, priceLtc);
			statement.setDouble(13,
				btc * priceBtc + bch * priceBch + eth * priceEth + ltc * priceLtc + usd
			);
			statement.executeUpdate();
			statement.close();
			con.commit();
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
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_trades"
				+ " (time,order_id,product,side,amount,price,executed_value,fees_usd) VALUES (?,?,?,?,?,?,?,?)");
			final long nanos = orderInfo.getTimeMicros() * 1000;
			Timestamp timestamp = new Timestamp((nanos / 1000000000L) * 1000);
			timestamp.setNanos((int) (nanos % 1000000000L));
			statement.setTimestamp(1, timestamp);
			statement.setString(2, orderInfo.getOrderId().toString());
			statement.setString(3, orderInfo.getProduct().toString());
			statement.setString(4, orderInfo.getOrderSide().toString());
			statement.setDouble(5, orderInfo.getFilledSize());
			statement.setDouble(6, orderInfo.getExecutedValue() / orderInfo.getFilledSize());
			statement.setDouble(7, orderInfo.getExecutedValue());
			statement.setDouble(8, orderInfo.getFillFees());
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record trade to database: " + orderInfo, e);
		}
	}

	/**
	 * Records trade to the database for a post-only fill (i.e. assumes we are the maker on the given match msg)
	 */
	public void recordPostOnlyFill(Match msg) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_trades"
				+ " (time,order_id,product,side,amount,price,executed_value,fees_usd) VALUES (?,?,?,?,?,?,?,?)");
			final long nanos = msg.getTimeMicros() * 1000;
			Timestamp timestamp = new Timestamp((nanos / 1000000000L) * 1000);
			timestamp.setNanos((int) (nanos % 1000000000L));
			statement.setTimestamp(1, timestamp);
			statement.setString(2, msg.getMakerOrderId().toString());
			statement.setString(3, msg.getProduct().toString());
			statement.setString(4, msg.getOrderSide().toString());
			statement.setDouble(5, msg.getSize());
			statement.setDouble(6, msg.getPrice());
			statement.setDouble(7, msg.getSize() * msg.getPrice());
			statement.setDouble(8, 0.0);
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record post-only fill to database " + msg.getOrderSide() + " " + msg.getSize()
				+ " of " + msg.getProduct() + " at " + msg.getPrice(), e);
		}
	}

	public void recordPostOnlyAttempt(TradeInstruction instruction, final UUID clientOid, final double price) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_posts"
				+ " (time,client_oid,product,side,amount,price,forecast) VALUES (?,?,?,?,?,?,?)");
			final long nanos = timeKeeper.epochNanos();
			Timestamp timestamp = new Timestamp((nanos / 1000000000L) * 1000);
			timestamp.setNanos((int) (nanos % 1000000000L));
			statement.setTimestamp(1, timestamp);
			statement.setString(2, clientOid.toString());
			statement.setString(3, instruction.getProduct().toString());
			statement.setString(4, instruction.getOrderSide().toString());
			statement.setDouble(5, instruction.getAmount());
			statement.setDouble(6, price);
			statement.setDouble(7, instruction.getForecast());
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record post-only attempt to database " + instruction + " at " + price, e);
		}
	}

	public void updatePostOnlyId(final UUID clientOid, final UUID orderId) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement =
				con.prepareStatement("INSERT INTO crypto_posts_client_oids (client_oid,order_id) VALUES (?,?)");
			statement.setString(1, clientOid.toString());
			statement.setString(2, orderId.toString());
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not update post with order id in database client_oid " + clientOid + " gdax order id "
				+ orderId, e);
		}
	}

	public void recordPostOnlyCancel(Done msg, final double originalAmount) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_posts_unfilled"
				+ " (time,order_id,product,side,original_amount,remaining_amount,price) VALUES (?,?,?,?,?,?,?)");
			final long nanos = msg.getTimeMicros() * 1000;
			Timestamp timestamp = new Timestamp((nanos / 1000000000L) * 1000);
			timestamp.setNanos((int) (nanos % 1000000000L));
			statement.setTimestamp(1, timestamp);
			statement.setString(2, msg.getOrderId().toString());
			statement.setString(3, msg.getProduct().toString());
			statement.setString(4, msg.getOrderSide().toString());
			statement.setDouble(5, originalAmount);
			statement.setDouble(6, msg.getRemainingSize());
			statement.setDouble(7, msg.getPrice());
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record cancel post to database " + msg.getOrderSide() + " " + msg.getRemainingSize()
				+ " of " + msg.getProduct() + " at " + msg.getPrice(), e);
		}
	}

	public SortedMap<ZonedDateTime,Double> getDailyPositionUsd()
		throws SQLException
	{
		final SortedMap<ZonedDateTime,Double> map = new TreeMap<>();
		Connection con = dataSource.getConnection();
		con.setAutoCommit(false);
		con.commit();
		PreparedStatement statement =
			con.prepareStatement("SELECT time,position_usd FROM crypto_hourly WHERE hour(time)=0");
		ResultSet results = statement.executeQuery();
		while(results.next()) {
			Timestamp timestamp = results.getTimestamp("time");
			Instant instant = Instant.ofEpochMilli(timestamp.getTime());
			ZonedDateTime dateTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
				.withHour(0)
				.withMinute(0)
				.withSecond(0)
				.withNano(0);
			double positionUsd = results.getDouble("position_usd");
			map.put(dateTime, positionUsd);
		}
		statement.close();
		con.close();
		return map;
	}

	/**
	 * Records trade to the database for a post-only fill with possible slippage
	 */
	public void recordPostOnlyFillWithSlippage(Match msg, UUID clientOid, double origPrice) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("INSERT INTO crypto_trades"
				+ " (time,client_oid,order_id,product,side,amount,price,executed_value,fees_usd,orig_price,slippage)"
				+ " VALUES (?,?,?,?,?,?,?,?,?,?,?)");
			final long nanos = msg.getTimeMicros() * 1000;
			Timestamp timestamp = new Timestamp((nanos / 1000000000L) * 1000);
			timestamp.setNanos((int) (nanos % 1000000000L));
			statement.setTimestamp(1, timestamp);
			statement.setString(2, clientOid.toString());
			statement.setString(3, msg.getMakerOrderId().toString());
			statement.setString(4, msg.getProduct().toString());
			statement.setString(5, msg.getOrderSide().toString());
			statement.setDouble(6, msg.getSize());
			statement.setDouble(7, msg.getPrice());
			statement.setDouble(8, msg.getSize() * msg.getPrice());
			statement.setDouble(9, 0.0);
			statement.setDouble(10, origPrice);
			statement.setDouble(11, Math.abs(origPrice - msg.getPrice()));
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record post-only fill with slippage to database " + msg.getOrderSide() + " "
				+ msg.getSize() + " of " + msg.getProduct() + " at " + msg.getPrice(), e);
		}
	}

	/**
	 * After a chasing post is done, updates with the filled amount, price, and slippage
	 */
	public void updatePostWithSlippage(UUID clientOid, double filledAmount, double filledPrice, double slippage) {
		try {
			Connection con = dataSource.getConnection();
			con.setAutoCommit(false);
			con.commit();
			PreparedStatement statement = con.prepareStatement("UPDATE crypto_posts"
				+ " SET filled_amount=?, filled_price=?, slippage=? WHERE client_oid=?");
			statement.setDouble(1, filledAmount);
			statement.setDouble(2, filledPrice);
			statement.setDouble(3, slippage);
			statement.setString(4, clientOid.toString());
			statement.executeUpdate();
			statement.close();
			con.commit();
			con.close();
		} catch(Exception e) {
			log.error("Could not record done post fill and slippage to database client oid " + clientOid + " "
				+ filledAmount + " at " + filledPrice + " slippage " + slippage, e);
		}
	}

	public void recordTieOutIfNew(Instant instant, double position, double simPosition, double ret, double simRet,
		String description)
		throws SQLException
	{
		Connection con = dataSource.getConnection();
		con.setAutoCommit(false);
		con.commit();
		PreparedStatement statement = con.prepareStatement("SELECT time FROM crypto_tieout WHERE time=?");
		Timestamp timestamp = new Timestamp(instant.toEpochMilli());
		statement.setTimestamp(1, timestamp);
		ResultSet results = statement.executeQuery();
		String date = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
		if(results.next()) {
			log.debug("Skipping date " + date);
			statement.close();
		} else {
			log.info("Found new date " + date + ", inserting");
			statement.close();

			statement = con.prepareStatement("INSERT INTO crypto_tieout"
				+ " (time,position_usd,sim_position_usd,ret,sim_ret,description) VALUES (?,?,?,?,?,?)");
			statement.setTimestamp(1, timestamp);
			statement.setDouble(2, position);
			statement.setDouble(3, simPosition);
			statement.setDouble(4, ret);
			statement.setDouble(5, simRet);
			statement.setString(6, description);
			statement.executeUpdate();
			statement.close();
		}
		con.commit();
		con.close();
	}

	public Connection getConnection()
		throws SQLException
	{
		return dataSource.getConnection();
	}
}
