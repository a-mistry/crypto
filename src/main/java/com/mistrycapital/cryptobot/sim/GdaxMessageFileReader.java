package com.mistrycapital.cryptobot.sim;

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
 * Reads all gdax message files (in zips) sequentially and gets each message string
 */
public class GdaxMessageFileReader implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Path dataDir;
	private final BlockingQueue<String> messageStringQueue;
	private final GdaxMessageTranslator writer;

	public GdaxMessageFileReader(Path dataDir, BlockingQueue<String> messageStringQueue, GdaxMessageTranslator writer) {
		this.dataDir = dataDir;
		this.messageStringQueue = messageStringQueue;
		this.writer = writer;
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

	long waitMs = 0;

	@Override
	public void run() {
		try {
			log.info("Looking for zip files in " + dataDir);
			final BiPredicate<Path,BasicFileAttributes> zipMatcher = (path, attributes) ->
				path.getFileName().toString().matches("gdax-orders-\\d\\d\\d\\d-\\d\\d-\\d\\d.*.zip");
			final List<Path> zips = Files
				.find(dataDir, 1, zipMatcher)
				.filter(GdaxMessageFileReader::hasNoCheckpoint)
				.collect(Collectors.toList());

			for(Path zipPath : zips) {
				ZipFile zipFile = new ZipFile(zipPath.toFile());
				List<ZipEntry> entries = zipFile.stream()
					.sorted(jsonSorter)
					.collect(Collectors.toList());

				for(ZipEntry entry : entries) {
					log.info("Reading entry " + entry.getName() + " in zip " + zipPath);
					InputStream inputStream = zipFile.getInputStream(entry);
					BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
					reader.lines().forEach(line -> {
						try {
							if(messageStringQueue.remainingCapacity() == 0) {
								Thread.sleep(1);
								waitMs += 10;
								if(waitMs % 1000 == 0)
									log.debug("Waited " + (waitMs/1000) + "s in string reader");
							}
							messageStringQueue.put(line);
						} catch(InterruptedException e) {
							throw new RuntimeException(e);
						}
					});
				}

				writeCheckpoint(zipPath);
			}
			writer.markDone();

		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	static Path getCheckpointPath(Path zipPath) {
		String checkpointName = zipPath.getFileName().toString().replace(".zip", "-checked.txt");
		return zipPath.getParent().resolve(checkpointName);
	}

	static boolean hasNoCheckpoint(Path zipPath) {
		return !Files.exists(getCheckpointPath(zipPath));
	}

	static void writeCheckpoint(Path zipPath)
		throws IOException
	{
		Files.createFile(getCheckpointPath(zipPath));
	}
}
