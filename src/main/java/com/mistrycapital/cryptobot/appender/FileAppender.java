package com.mistrycapital.cryptobot.appender;

import java.io.IOException;

public interface FileAppender {
	/**
	 * Opens the appender for writing.
	 */
	void open() throws IOException;

	/**
	 * Append the given message to the file, with a newline inserted after
	 */
	void append(String msg) throws IOException;

	/**
	 * Close this appender. After close() is called, no further appending can be done unless it is opened again
	 */
	void close() throws IOException;
}
