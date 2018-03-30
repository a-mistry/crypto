package com.mistrycapital.cryptobot.appender;

import java.io.IOException;

public interface FileAppender {
	/**
	 * Append the given message to the file, with a newline inserted after
	 */
	void append(String msg) throws IOException;

	/**
	 * Close this appender. After close() is called, no further appending can be done under any circumstances
	 */
	void close() throws IOException;
}
