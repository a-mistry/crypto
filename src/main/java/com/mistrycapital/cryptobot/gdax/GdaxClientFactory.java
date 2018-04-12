package com.mistrycapital.cryptobot.gdax;

import com.mistrycapital.cryptobot.gdax.client.GdaxClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class GdaxClientFactory {
	/**
	 * @return Gdax client constructed with API credentials stored in the file given in properties
	 */
	public static GdaxClient createClientFromFile(Path apiFile)
		throws IOException
	{
		List<String> apiLines = Files.lines(apiFile).collect(Collectors.toList());
		if(apiLines.size() < 3)
			throw new IOException("Gdax credentials file contains fewer than 3 lines");
		return new GdaxClient(apiLines.get(0), apiLines.get(1), apiLines.get(2));
	}
}
