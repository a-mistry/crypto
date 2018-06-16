package com.mistrycapital.cryptobot.gdax.websocket;

import com.google.gson.JsonObject;
import com.mistrycapital.cryptobot.gdax.common.Product;

import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class CommonGdaxMessage implements GdaxMessage {
	/** UTC time in microseconds */
	protected final long timeMicros;
	/** Product */
	protected final Product product;
	/** Sequence number */
	protected final long sequence;

	CommonGdaxMessage(JsonObject json) {
		timeMicros = parseTimeMicros(json.get("time").getAsString());
		product = Product.parse(json.get("product_id").getAsString());
		sequence = json.get("sequence").getAsLong();
	}

	private static Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
	//"2014-11-07T08:19:28.464459Z"
	private static final Pattern gdaxIsoPattern =
		Pattern.compile("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)T(\\d\\d):(\\d\\d):(\\d\\d)\\.?(\\d*)Z");


	static final long parseTimeMicrosOld(String timeString) {
		final Matcher matcher = gdaxIsoPattern.matcher(timeString);
		if(!matcher.matches()) {
			return 0;
		} else {
			final int year = Integer.parseInt(matcher.group(1));
			final int month = Integer.parseInt(matcher.group(2));
			final int day = Integer.parseInt(matcher.group(3));
			final int hour = Integer.parseInt(matcher.group(4));
			final int min = Integer.parseInt(matcher.group(5));
			final int sec = Integer.parseInt(matcher.group(6));
			final String microString = matcher.group(7);
			final int micros = microString.equals("") ? 0 : Integer.parseInt(microString);
			utcCal.set(year, month - 1, day, hour, min, sec);
			final long seconds = utcCal.getTimeInMillis() / 1000L;
			return seconds * 1000000L + micros;
		}
	}

	/** Highly optimized time parser. This is useful because profiling results showed ~20% of message parsing
	 * time was spent in parsing time strings.
	 * @param timeString Time string in the format 2014-11-07T08:19:28.464459Z or 2014-11-07T08:19:28Z
	 * @return Time in micros since epoch
	 */
	static final long parseTimeMicros(String timeString) {
		char[] timeChars = timeString.toCharArray();
		final int year = parseIntFromChars(timeChars, 0, 4);
		final int month = parseIntFromChars(timeChars, 5, 7);
		final int day = parseIntFromChars(timeChars, 8, 10);
		final int hour = parseIntFromChars(timeChars, 11, 13);
		final int min = parseIntFromChars(timeChars, 14, 16);
		final int sec = parseIntFromChars(timeChars, 17, 19);
		final int micros;
		if(timeChars.length == 20)
			micros = 0;
		else
			micros = parseIntFromChars(timeChars, 20, timeChars.length-1);
		utcCal.set(year, month - 1, day, hour, min, sec);
		final long seconds = utcCal.getTimeInMillis() / 1000L;
		return seconds * 1000000L + micros;
	}

	/**
	 * Parses the characters in the array starting at startIdx and ending at endIdx-1
	 * @return int value of the given characters
	 */
	static final int parseIntFromChars(char[] chars, int startIdx, int endIdx) {
		if(startIdx>=chars.length || endIdx>chars.length)
			throw new NumberFormatException();

		int retVal = 0;
		for(int i=startIdx; i<endIdx; i++) {
			char ch = chars[i];
			if(ch < '0' || ch > '9')
				throw new NumberFormatException();

			retVal *= 10;
			retVal += ch - '0';
		}
		return retVal;
	}

	@Override
	public final Product getProduct() {
		return product;
	}

	@Override
	public final long getTimeMicros() {
		return timeMicros;
	}

	@Override
	public final long getSequence() {
		return sequence;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(getType().toString());
		builder.append(" message received at ");
		builder.append(timeMicros);
		builder.append(" sequence ");
		builder.append(sequence);
		builder.append(" for ");
		builder.append(product);
		return builder.toString();
	}
}