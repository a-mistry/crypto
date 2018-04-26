package com.mistrycapital.cryptobot.gdax;

import com.mistrycapital.cryptobot.gdax.client.GdaxClient;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class GdaxClientFactory {
	private static final Logger log = MCLoggerFactory.getLogger();

	/**
	 * @return Gdax client constructed with API credentials stored in the file given in properties
	 */
	public static GdaxClient createClientFromFile(Path apiFile)
		throws IOException
	{
		List<String> apiLines = Files.lines(apiFile).collect(Collectors.toList());
		if(apiLines.size() < 3)
			throw new IOException("Gdax credentials file contains fewer than 3 lines");
		
		try {
			return new GdaxClient(new URI("https://api.gdax.com"), apiLines.get(0), apiLines.get(1), apiLines.get(2));
		} catch(URISyntaxException e) {
			log.error("Invalid gdax api uri", e);
			throw new RuntimeException(e);
		}
	}
}
