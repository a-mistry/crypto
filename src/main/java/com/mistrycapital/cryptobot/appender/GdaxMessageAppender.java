package com.mistrycapital.cryptobot.appender;

import java.io.IOException;
import java.nio.file.Path;

import com.mistrycapital.cryptobot.book.OrderBook;
import com.mistrycapital.cryptobot.book.OrderBookManager;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

/**
 * Appends gdax messages to a log file, with a new file used every hour. When we roll to a new file,
 * it will add book information at the beginning
 */
public class GdaxMessageAppender extends CommonFileAppender {
	private final OrderBookManager orderBookManager;

	public GdaxMessageAppender(final Path dataDir, final String baseFilename, String extension,
		final TimeKeeper timeKeeper, final OrderBookManager orderBookManager)
	{
		super(timeKeeper, dataDir, baseFilename, extension, RollingPolicy.HOURLY, FlushPolicy.DONT_FLUSH);
		this.orderBookManager = orderBookManager;
	}

	@Override
	protected void addNewFileHeader()
		throws IOException
	{
		for(Product product : Product.FAST_VALUES) {
			final OrderBook book = orderBookManager.getBook(product);
			if(book != null) {
				final String snapshot = book.getGdaxSnapshot();
				append(snapshot);
			}
		}

	}
}