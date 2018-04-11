package com.mistrycapital.cryptobot.gdax;

import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.mistrycapital.cryptobot.util.MCProperties;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class GdaxClientFactory {
	private static final Logger log = MCLoggerFactory.getLogger();

	/**
	 * @return Gdax client constructed with API credentials stored in the file given in properties
	 */
	public static GdaxClient createClientFromFile()
		throws IOException
	{
		String apiFilename = MCProperties.getProperty("gdax.client.apiFile");
		log.debug("Reading gdax credentials from " + apiFilename);
		Path apiFile = Paths.get(apiFilename);
		List<String> apiLines = Files.lines(apiFile).collect(Collectors.toList());
		if(apiLines.size() < 3)
			throw new IOException("Gdax credentials file contains fewer than 3 lines");
		return new GdaxClient(apiLines.get(0), apiLines.get(1), apiLines.get(2));
	}
}
