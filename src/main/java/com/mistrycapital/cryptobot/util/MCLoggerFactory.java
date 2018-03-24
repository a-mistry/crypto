package com.mistrycapital.cryptobot.util;

import java.lang.StackWalker.Option;

import org.slf4j.Logger;

public class MCLoggerFactory {
	/**
	 * @return Default logger to use for the calling class
	 */
	public static Logger getLogger() {
		return org.slf4j.LoggerFactory.getLogger(StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass());
	}
}