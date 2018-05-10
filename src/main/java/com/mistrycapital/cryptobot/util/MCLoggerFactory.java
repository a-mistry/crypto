package com.mistrycapital.cryptobot.util;

import java.lang.StackWalker.Option;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCLoggerFactory {
	/**
	 * @return Default logger to use for the calling class
	 */
	public static Logger getLogger() {
		return LoggerFactory.getLogger(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
	}

	/**
	 * Turn off debug logging in sim
	 */
	public static void setSimLogLevel() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		context.getLogger("com.mistrycapital").setLevel(Level.INFO);
	}
}