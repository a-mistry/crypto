package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.Intervalizer;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public class DailyAppender extends CommonFileAppender {
	private static final Logger log = MCLoggerFactory.getLogger();

	private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

	private final TimeKeeper timeKeeper;
	private final Intervalizer intervalizer;
	private final Accountant accountant;
	private double prevTotalPositionUsd;
	private long nextDayMillis;

	public DailyAppender(Accountant accountant, TimeKeeper timeKeeper, Intervalizer intervalizer, Path dataDir, String filename) {
		super(timeKeeper, dataDir, filename.replace(".csv", ""), ".csv", RollingPolicy.NEVER,
			FlushPolicy.FLUSH_EACH_WRITE);
		this.timeKeeper = timeKeeper;
		this.intervalizer = intervalizer;
		this.accountant = accountant;
		nextDayMillis = timeKeeper.epochMs();
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		StringBuilder builder = new StringBuilder();
		builder.append("date,unixTimestamp,Usd,");
		for(Product product : Product.FAST_VALUES) {
			Currency crypto = product.getCryptoCurrency();
			builder.append(crypto + "Pos," + crypto + "Mid,");
		}
		builder.append("TotalPositionUsd,Return1d");
		append(builder.toString());
	}

	public void writeDaily(ConsolidatedSnapshot snapshot)
		throws IOException
	{
		if(timeKeeper.epochMs() < nextDayMillis)
			return;

		nextDayMillis = intervalizer.calcNextDayMillis(timeKeeper.epochMs());
		StringBuilder builder = new StringBuilder();
		builder.append(timeKeeper.iso8601().split("T")[0]);
		builder.append(',');
		builder.append(timeKeeper.epochMs() / 1000L);
		builder.append(',');
		builder.append(accountant.getAvailable(Currency.USD));
		builder.append(',');
		for(Product product : Product.FAST_VALUES) {
			builder.append(accountant.getAvailable(product.getCryptoCurrency()));
			builder.append(',');
			builder.append(snapshot.getProductSnapshot(product).midPrice);
			builder.append(',');
		}
		final double totalPositionUsd = accountant.getPositionValueUsd(snapshot);
		builder.append(totalPositionUsd);
		builder.append(',');
		if(prevTotalPositionUsd != 0.0)
			builder.append(totalPositionUsd / prevTotalPositionUsd - 1.0);
		prevTotalPositionUsd = totalPositionUsd;
		append(builder.toString());
	}
}

