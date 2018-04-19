package com.mistrycapital.cryptobot.sim;

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

public class DecisionLogger {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final TimeKeeper timeKeeper;
	private final Accountant accountant;
	private final Path decisionFile;
	private BufferedWriter writer;

	public DecisionLogger(Accountant accountant, TimeKeeper timeKeeper, Path decisionFile) {
		this.timeKeeper = timeKeeper;
		this.accountant = accountant;
		this.decisionFile = decisionFile;
	}

	public void open() {
		try {
			writer =
				Files.newBufferedWriter(decisionFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			writer.write("date,unixTimestamp,USD,");
			for(Product product : Product.FAST_VALUES) {
				Currency crypto = product.getCryptoCurrency();
				writer.write(
					"MidPrice" + crypto + ",Forecast" + crypto + ",AmountTraded" + crypto + ",Action" + crypto +
						",Pos" + crypto + ",");
			}
			writer.write("TotalPositionUsd");
			writer.newLine();
		} catch(IOException e) {
			log.error("Could not open decision log", e);
		}
	}

	public void close() {
		try {
			writer.close();
		} catch(IOException e) {
			log.error("Could not close decision log", e);
		}
	}

	public void logDecision(ConsolidatedSnapshot snapshot, double[] forecasts, List<TradeInstruction> tradeInstructions)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(timeKeeper.iso8601());
		builder.append(',');
		builder.append(timeKeeper.epochMs() / 1000L);
		builder.append(',');
		builder.append(accountant.getAvailable(Currency.USD));
		builder.append(',');
		for(Product product : Product.FAST_VALUES) {
			builder.append(snapshot.getProductSnapshot(product).midPrice);
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

		try {
			writer.write(builder.toString());
			writer.newLine();
		} catch(IOException e) {
			log.error("Could not record decision log", e);
		}
	}
}
