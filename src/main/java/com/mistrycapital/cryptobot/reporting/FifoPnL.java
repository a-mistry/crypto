package com.mistrycapital.cryptobot.reporting;

import com.mistrycapital.cryptobot.database.DBRecorder;
import com.mistrycapital.cryptobot.sim.SimTimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.Properties;

public class FifoPnL {
	private static final Logger log = MCLoggerFactory.getLogger();

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

		Connection con = dbRecorder.getConnection();
		PreparedStatement statement = con.prepareStatement("SELECT * FROM crypto_trades");
		ResultSet trades = statement.executeQuery();
		PreparedStatement statement2 = con.prepareStatement("SELECT * FROM crypto_hourly");
		ResultSet hourly = statement2.executeQuery();

		long hourMs = hourly.next() ? hourly.getTimestamp("time").getTime() : 0;
		SimTimeKeeper timeKeeper = new SimTimeKeeper();
		while(trades.next()) {
			long tradeTimeMs = trades.getTimestamp("time").getTime();
			if(tradeTimeMs < hourMs) {
				timeKeeper.advanceTime(tradeTimeMs * 1000000L);
				// add buys to queue, pop and match on sells
				System.out.println("Found trade at " + timeKeeper.iso8601() + " " + trades.getString("side"));
			}
			// reset all position queues here
			if(tradeTimeMs > hourMs && hourly.next()) {
				hourMs = hourly.getTimestamp("time").getTime();
				if(hourly.getDouble("usd") == hourly.getDouble("position_usd")) {
					timeKeeper.advanceTime(hourMs * 1000000L);
					System.out.println("Zero position at " + timeKeeper.iso8601());
				}
			}
		}

		statement.close();
		statement2.close();
		con.close();
	}
}
