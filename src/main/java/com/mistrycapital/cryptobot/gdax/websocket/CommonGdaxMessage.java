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


	static final long parseTimeMicros(String timeString) {
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