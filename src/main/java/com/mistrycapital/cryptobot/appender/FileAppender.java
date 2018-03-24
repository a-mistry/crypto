package com.mistrycapital.cryptobot.appender;

import java.io.IOException;

public interface FileAppender {
	/**
	 * Append the given message to the file, with a newline inserted after
	 */
	public void append(String msg) throws IOException;

	/**
	 * Close this appender
	 */
	public void close() throws IOException;
}
