package com.mistrycapital.cryptobot.sim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads all gdax message files (in zips) sequentially and parses objects
 */
public class GdaxMessageFileReader implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Path dataDir;
	private final BlockingQueue<GdaxMessage> messageQueue;
	private final GdaxSampleWriter writer;
	private final JsonParser jsonParser;

	public GdaxMessageFileReader(Path dataDir, BlockingQueue<GdaxMessage> messageQueue, GdaxSampleWriter writer) {
		this.dataDir = dataDir;
		this.messageQueue = messageQueue;
		this.writer = writer;
		jsonParser = new JsonParser();
	}

	final private Pattern hourlyJsonPattern = Pattern.compile("gdax-orders-\\d\\d\\d\\d-\\d\\d-\\d\\d-(\\d*).json");
	final Comparator<ZipEntry> jsonSorter = (a, b) -> {
		final String aStr = a.getName();
		final String bStr = b.getName();
		Matcher aMatcher = hourlyJsonPattern.matcher(aStr);
		if(aMatcher.matches()) {
			Matcher bMatcher = hourlyJsonPattern.matcher(bStr);
			if(bMatcher.matches()) {
				int aHour = Integer.parseInt(aMatcher.group(1));
				int bHour = Integer.parseInt(bMatcher.group(1));
				return Integer.compare(aHour, bHour);
			}
		}
		return aStr.compareTo(bStr);
	};


	@Override
	public void run() {
		try {
			log.info("Looking for zip files in " + dataDir);
			final BiPredicate<Path,BasicFileAttributes> zipMatcher = (path, attributes) ->
				path.getFileName().toString().matches("gdax-orders-\\d\\d\\d\\d-\\d\\d-\\d\\d.*.zip");
			final List<Path> zips = Files
				.find(dataDir, 1, zipMatcher)
				.collect(Collectors.toList());

			for(Path zipPath : zips) {
				ZipFile zipFile = new ZipFile(zipPath.toFile());
				List<ZipEntry> entries = zipFile.stream()
					.sorted(jsonSorter)
					.collect(Collectors.toList());

				for(ZipEntry entry : entries) {
					log.info("Reading entry " + entry.getName() + " in zip " + zipPath);
					InputStream inputStream = zipFile.getInputStream(entry);
					BufferedReader jsonReader = new BufferedReader(new InputStreamReader(inputStream));
					readJson(jsonReader);
				}
			}
			writer.markDone();

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void readJson(BufferedReader jsonReader) {
		jsonReader.lines().forEach(line -> {
			try {
				GdaxMessage gdaxMessage = parseMessage(line);
				if(gdaxMessage != null)
					messageQueue.add(gdaxMessage);
			} catch(Exception e) {
				log.error("Error in parsing line: " + line, e);
			}
		});
	}

	private GdaxMessage parseMessage(String msgStr) {
		JsonObject json = jsonParser.parse(msgStr).getAsJsonObject();
		String type = json.get("type").getAsString();
		GdaxMessage message = null;
		switch(type) {
			case "book":
			case "order_book":
				// saved full book data
				message = new Book(json);
				break;
			case "open":
				// new order on book
				message = new Open(json);
				break;
			case "done":
				// order taken off book - could be filled or canceled
				message = new Done(json);
				break;
			case "match":
				// trade occurred
				message = new Match(json);
				break;
			case "change":
				// order modified
				if(json.has("new_size")) {
					message = new ChangeSize(json);
				} else {
					message = new ChangeFunds(json);
				}
				break;
			case "activate":
				// new stop order
				message = new Activate(json);
				break;
			case "received":
				// received a new order - we don't really do anything with these
			default:
				// this could be a new message type or received
				// or something else
		}
		json = null; // mark for GC
		return message;
	}
}
