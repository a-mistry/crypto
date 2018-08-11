package com.mistrycapital.cryptobot.appender;

import com.mistrycapital.cryptobot.accounting.Accountant;
import com.mistrycapital.cryptobot.aggregatedata.ConsolidatedSnapshot;
import com.mistrycapital.cryptobot.execution.TradeInstruction;
import com.mistrycapital.cryptobot.gdax.common.Currency;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class DecisionAppender extends CommonFileAppender {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;

	public DecisionAppender(Accountant accountant, TimeKeeper timeKeeper, Path dataDir, String filename) {
		super(timeKeeper, dataDir, filename.replace(".csv", ""), ".csv", RollingPolicy.NEVER,
			FlushPolicy.FLUSH_EACH_WRITE);
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		StringBuilder builder = new StringBuilder();
		builder.append("date,unixTimestamp,USD,");
		for(Product product : Product.FAST_VALUES) {
			Currency crypto = product.getCryptoCurrency();
			builder.append("MidPrice" + crypto + ",Forecast" + crypto + ",AmountTraded" + crypto + ",Action" + crypto
				+ ",Pos" + crypto + ",");
		}
		builder.append("TotalPositionUsd");
		append(builder.toString());
	}

	public void logDecision(ConsolidatedSnapshot snapshot, double[] forecasts, List<TradeInstruction> tradeInstructions)
		throws IOException
	{
		StringBuilder builder = new StringBuilder();
		builder.append(timeKeeper.iso8601());
		builder.append(',');
		builder.append(timeKeeper.epochMs() / 1000L);
		builder.append(',');
		builder.append(accountant.getAvailable(Currency.USD));
		builder.append(',');
		for(Product product : Product.FAST_VALUES) {
			final var productSnapshot = snapshot.getProductSnapshot(product);
			builder.append(productSnapshot == null ? Double.NaN : productSnapshot.midPrice);
			builder.append(',');
			builder.append(forecasts[product.getIndex()]);
			builder.append(',');
			if(tradeInstructions == null) {
				builder.append(Double.NaN);
				builder.append(",NA,");
			} else {
				final var tradeOpt = tradeInstructions.stream()
					.filter(tradeInstruction -> tradeInstruction.getProduct() == product)
					.findFirst();
				final var amountOpt = tradeOpt.map(instruction -> instruction.getAmount());
				final var sideOpt = tradeOpt.map(instruction -> instruction.getOrderSide().toString());
				builder.append(amountOpt.orElse(Double.NaN));
				builder.append(',');
				builder.append(sideOpt.orElse("NA"));
				builder.append(',');
			}
			builder.append(accountant.getAvailable(product.getCryptoCurrency()));
			builder.append(',');
		}
		builder.append(accountant.getPositionValueUsd(snapshot));
		append(builder.toString());
	}
}
