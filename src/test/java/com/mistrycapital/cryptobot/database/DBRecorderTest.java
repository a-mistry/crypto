package com.mistrycapital.cryptobot.database;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.book.BBO;
import com.mistrycapital.cryptobot.book.Order;
import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.execution.Aggression;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.client.OrderInfo;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.OrderSide;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.gdax.websocket.Done;
import com.mistrycapital.cryptobot.gdax.websocket.Match;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCProperties;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DBRecorderTest {
	private static final double EPSILON = 0.00000001;

	private static SimTimeKeeper timeKeeper;
	private static Accountant accountant;
	private static OrderBookManager orderBookManager;
	private static DBRecorder dbRecorder;
	private static MysqlDataSource dataSource;

	@BeforeAll
	static void setUp()
		throws Exception
	{
		String databaseName = "test_mistrycapital";
		MCProperties properties = new MCProperties();
		Path credentialsFile = Paths.get(properties.getProperty("credentialsFile"));
		Properties credentials = new Properties();
		try(Reader reader = Files.newBufferedReader(credentialsFile, StandardCharsets.UTF_8)) {
			credentials.load(reader);
		}
		String user = credentials.getProperty("MysqlUser");
		String password = credentials.getProperty("MysqlPassword");
		dataSource = new MysqlDataSource();
		dataSource.setServerName("mistry.info");
		dataSource.setPort(3306);
		dataSource.setDatabaseName(databaseName);
		dataSource.setUser(user);
		dataSource.setPassword(password);
		dataSource.setRequireSSL(true);
		dataSource.setServerTimezone("UTC");

		timeKeeper = new SimTimeKeeper();
		timeKeeper.advanceTime(System.currentTimeMillis() * 1000000L);
		accountant = mock(Accountant.class);
		orderBookManager = mock(OrderBookManager.class);
		dbRecorder = new DBRecorder(timeKeeper, accountant, orderBookManager, databaseName, user, password);
	}

	@Test
	void shouldRecordPositions()
		throws Exception
	{
		var positions = new HashMap<Currency,Double>();
		positions.put(Currency.USD, 1234.0);
		positions.put(Currency.BTC, 7.0);
		positions.put(Currency.BCH, 9.0);
		positions.put(Currency.ETH, 11.0);
		positions.put(Currency.LTC, 14.0);
		positions.put(Currency.EUR, 19.0);
		positions.put(Currency.GBP, 19.0);
		for(Currency currency : Currency.FAST_VALUES) {
			when(accountant.getBalance(currency)).thenReturn(positions.get(currency));
		}
		var prices = new HashMap<Product,Double>();
		prices.put(Product.BTC_USD, 20.0);
		prices.put(Product.BCH_USD, 23.0);
		prices.put(Product.ETH_USD, 42.0);
		prices.put(Product.LTC_USD, 37.0);
		for(Product product : Product.FAST_VALUES) {
			OrderBook orderBook = mock(OrderBook.class);
			BBO bbo = new BBO();
			bbo.bidPrice = bbo.askPrice = prices.get(product);
			when(orderBook.getBBO()).thenReturn(bbo);
			when(orderBookManager.getBook(product)).thenReturn(orderBook);
		}

		dbRecorder.recordDailyPositions();
		dbRecorder.recordHourlyPositions();

		// verify db
		Connection con = dataSource.getConnection();
		con.setAutoCommit(false);
		con.commit();
		PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_positions WHERE time=?");
		statement.setTimestamp(1, new Timestamp(timeKeeper.epochMs()));
		ResultSet results = statement.executeQuery();
		assertTrue(results.next());
		do {
			Currency currency = Currency.valueOf(results.getString("currency"));
			assertEquals(positions.get(currency), results.getDouble("position"), EPSILON);
			if(currency.isCrypto())
				assertEquals(prices.get(currency.getUsdProduct()), results.getDouble("price"), EPSILON);
			else
				assertEquals(0.0, results.getDouble("price"));
		} while(results.next());
		statement.close();
		statement = con.prepareStatement("SELECT * FROM crypto_hourly WHERE time=?");
		statement.setTimestamp(1, new Timestamp(timeKeeper.epochMs()/1000*1000));
		results = statement.executeQuery();
		assertTrue(results.next());
		for(Currency currency : Currency.FAST_VALUES) {
			assertEquals(positions.get(currency), results.getDouble(currency.toString().toLowerCase(Locale.US)),
				EPSILON);
		}
		var positionUsd = positions.get(Currency.USD);
		for(Product product : Product.FAST_VALUES) {
			assertEquals(prices.get(product),
				results.getDouble("price_" + product.getCryptoCurrency().toString().toLowerCase(Locale.US)), EPSILON);
			positionUsd += prices.get(product) * positions.get(product.getCryptoCurrency());
		}
		assertEquals(positionUsd, results.getDouble("position_usd"), EPSILON);
		statement.close();
		con.close();
	}

	@Test
	void recordTrade()
		throws Exception
	{
		JsonParser parser = new JsonParser();
		JsonObject json = parser.parse("{\n" +
			"    \"id\": \"68e6a28f-ae28-4788-8d4f-5ab4e5e5ae08\",\n" +
			"    \"size\": \"99.0\",\n" +
			"    \"product_id\": \"ETH-USD\",\n" +
			"    \"side\": \"sell\",\n" +
			"    \"stp\": \"dc\",\n" +
			"    \"funds\": \"9.9750623400000000\",\n" +
			"    \"specified_funds\": \"10.0000000000000000\",\n" +
			"    \"type\": \"market\",\n" +
			"    \"post_only\": false,\n" +
			"    \"created_at\": \"" + timeKeeper.iso8601() + "\",\n" +
			"    \"done_at\": \"2016-12-08T20:09:05.527Z\",\n" +
			"    \"done_reason\": \"filled\",\n" +
			"    \"fill_fees\": \"1.0\",\n" +
			"    \"filled_size\": \"2.0\",\n" +
			"    \"executed_value\": \"202.0\",\n" +
			"    \"status\": \"done\",\n" +
			"    \"settled\": true\n" +
			"}").getAsJsonObject();
		OrderInfo orderInfo = new OrderInfo(json);
		assertEquals(timeKeeper.epochNanos() / 1000L, orderInfo.getTimeMicros());

		dbRecorder.recordTrade(orderInfo);

		Connection con = dataSource.getConnection();
		PreparedStatement statement = con.prepareStatement(
			"SELECT * FROM crypto_trades a INNER JOIN (SELECT MAX(row_id) as row_id FROM crypto_trades) b ON a.row_id=b.row_id");
		ResultSet results = statement.executeQuery();
		assertTrue(results.next());
		assertEquals(timeKeeper.epochNanos() / 1000000000L, results.getTimestamp("time").getTime() / 1000L);
		assertEquals(timeKeeper.epochNanos() % 1000000L, results.getTimestamp("time").getNanos());
		assertEquals("ETH-USD", results.getString("product"));
		assertEquals("SELL", results.getString("side"));
		assertEquals(2.0, results.getDouble("amount"), EPSILON);
		assertEquals(101.0, results.getDouble("price"), EPSILON);
		assertEquals(202.0, results.getDouble("executed_value"), EPSILON);
		assertEquals(1.0, results.getDouble("fees_usd"), EPSILON);
		assertFalse(results.next());
		con.close();
	}

	@Test
	public void shouldRecordPost()
		throws Exception
	{
		UUID clientOid = UUID.randomUUID();
		TradeInstruction instruction =
			new TradeInstruction(Product.ETH_USD, 3.14, OrderSide.BUY, Aggression.POST_ONLY, 0.005);
		dbRecorder.recordPostOnlyAttempt(instruction, clientOid, 50.0);

		UUID orderId = UUID.randomUUID();
		dbRecorder.updatePostOnlyId(clientOid, orderId);

		// fill part and cancel part
		JsonParser jsonParser = new JsonParser();
		JsonObject json = jsonParser.parse("{\n" +
			"    \"type\": \"match\",\n" +
			"    \"trade_id\": 10,\n" +
			"    \"sequence\": 50,\n" +
			"    \"maker_order_id\": \"" + orderId + "\",\n" +
			"    \"taker_order_id\": \"132fb6ae-456b-4654-b4e0-d681ac05cea1\",\n" +
			"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
			"    \"product_id\": \"ETH-USD\",\n" +
			"    \"size\": \"2.0\",\n" +
			"    \"price\": \"50.0\",\n" +
			"    \"side\": \"buy\"\n" +
			"}").getAsJsonObject();
		dbRecorder.recordPostOnlyFill(new Match(json));
		json = jsonParser.parse("{\n" +
			"    \"type\": \"done\",\n" +
			"    \"time\": \"2014-11-07T08:19:27.028459Z\",\n" +
			"    \"product_id\": \"ETH-USD\",\n" +
			"    \"sequence\": 10,\n" +
			"    \"price\": \"50.0\",\n" +
			"    \"order_id\": \"" + orderId + "\",\n" +
			"    \"reason\": \"canceled\",\n" +
			"    \"side\": \"buy\",\n" +
			"    \"remaining_size\": \"1.14\"\n" +
			"}").getAsJsonObject();
		dbRecorder.recordPostOnlyCancel(new Done(json), 3.14);

		Connection con = dataSource.getConnection();
		PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_posts WHERE client_oid=?");
		statement.setString(1, clientOid.toString());
		ResultSet results = statement.executeQuery();
		assertTrue(results.next());
		// IMPORTANT NOTE
		// Mysql does not resolve milliseconds; it will round to the nearest second. So we have no need to check nanos here
		// and need to put a second tolerance
		assertEquals(timeKeeper.epochNanos() / 1000000000L, results.getTimestamp("time").getTime() / 1000L, 1);
		assertEquals("ETH-USD", results.getString("product"));
		assertEquals("BUY", results.getString("side"));
		assertEquals(3.14, results.getDouble("amount"), EPSILON);
		assertEquals(50.0, results.getDouble("price"), EPSILON);
		assertEquals(0.005, results.getDouble("forecast"), EPSILON);
		assertFalse(results.next());
		statement.close();
		statement = con.prepareStatement("SELECT * FROM crypto_posts_client_oids WHERE client_oid=?");
		statement.setString(1, clientOid.toString());
		results = statement.executeQuery();
		assertTrue(results.next());
		assertEquals(orderId.toString(), results.getString("order_id"));
		assertFalse(results.next());
		statement.close();
		statement = con.prepareStatement("SELECT * FROM crypto_trades WHERE order_id=?");
		statement.setString(1, orderId.toString());
		results = statement.executeQuery();
		assertTrue(results.next());
		final long jsonTimeNanos = 1415348367028459000L;
		assertEquals(jsonTimeNanos / 1000000000L, results.getTimestamp("time").getTime() / 1000L, 1);
		assertEquals("ETH-USD", results.getString("product"));
		assertEquals("BUY", results.getString("side"));
		assertEquals(2.0, results.getDouble("amount"), EPSILON);
		assertEquals(50.0, results.getDouble("price"), EPSILON);
		assertEquals(100.0, results.getDouble("executed_value"), EPSILON);
		assertEquals(0.0, results.getDouble("fees_usd"), EPSILON);
		assertFalse(results.next());
		statement.close();
		statement = con.prepareStatement("SELECT * FROM crypto_posts_unfilled WHERE order_id=?");
		statement.setString(1, orderId.toString());
		results = statement.executeQuery();
		assertTrue(results.next());
		assertEquals(jsonTimeNanos / 1000000000L, results.getTimestamp("time").getTime() / 1000L, 1);
		assertEquals("ETH-USD", results.getString("product"));
		assertEquals("BUY", results.getString("side"));
		assertEquals(3.14, results.getDouble("original_amount"), EPSILON);
		assertEquals(1.14, results.getDouble("remaining_amount"), EPSILON);
		assertEquals(50.0, results.getDouble("price"), EPSILON);
		assertFalse(results.next());
		statement.close();
		con.close();
	}
}