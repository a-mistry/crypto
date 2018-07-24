package com.mistrycapital.cryptobot.sim;

import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads all gdax message files (in zips) sequentially and gets each message string
 */
public class GdaxMessageFileReader implements Runnable {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final Path dataDir;
	private final BlockingQueue<String> messageStringQueue;
	private final DoneNotificationRecipient writer;

	public GdaxMessageFileReader(Path dataDir, BlockingQueue<String> messageStringQueue,
		DoneNotificationRecipient writer)
	{
		this.dataDir = dataDir;
		this.messageStringQueue = messageStringQueue;
		this.writer = writer;
	}

	private static final Pattern hourlyJsonPattern =
		Pattern.compile("gdax-orders-\\d\\d\\d\\d-\\d\\d-\\d\\d-(\\d*).json");
	/** Sorts the same day's json entries by the hour */
	static final Comparator<ZipEntry> hourlyJsonSorter = (a, b) -> {
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
			for(Path zipPath : getZipFiles()) {
				try {
					long startNanos = System.nanoTime();
					readZipFile(zipPath);
					writeCheckpoint(zipPath);
					log.info("Completed " + zipPath + " in " + (System.nanoTime() - startNanos) / 1000000000.0 + "s");
				} catch(ZipException e) {
					log.error("Could not parse zip " + zipPath, e);
				}
			}
			writer.markDone();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return List of all files in the dataDir folder that match gdax-orders-yyyy-MM-dd.zip
	 */
	private List<Path> getZipFiles()
		throws IOException
	{
		final BiPredicate<Path,BasicFileAttributes> zipMatcher = (path, attributes) ->
			path.getFileName().toString().matches("gdax-orders-\\d\\d\\d\\d-\\d\\d-\\d\\d.*.zip");

		log.info("Looking for zip files in " + dataDir);
		return Files
			.find(dataDir, 1, zipMatcher)
			.filter(GdaxMessageFileReader::hasNoCheckpoint)
			.collect(Collectors.toList());
	}

	/**
	 * Reads the zip file, sorts entries by hour, and reads the entries
	 */
	public void readZipFile(Path zipPath)
		throws IOException
	{
		ZipFile zipFile = new ZipFile(zipPath.toFile());
		List<ZipEntry> entries = zipFile.stream()
			.sorted(hourlyJsonSorter)
			.collect(Collectors.toList());

		for(ZipEntry entry : entries) {
			log.info("Reading entry " + entry.getName() + " in zip " + zipPath);
			InputStream inputStream = zipFile.getInputStream(entry);
			readJsonFile(inputStream);
		}
	}

	/**
	 * Reads the JSON file from the given input stream and sends all messages to the queue
	 */
	private void readJsonFile(InputStream inputStream) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		reader.lines().forEach(line -> {
			try {
				if(messageStringQueue.remainingCapacity() == 0) {
					Thread.sleep(1);
					waitMs += 10;
					if(waitMs % 100000 == 0)
						log.debug("Waited " + (waitMs / 1000) + "s in string reader");
				}
				messageStringQueue.put(line);
			} catch(InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
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
