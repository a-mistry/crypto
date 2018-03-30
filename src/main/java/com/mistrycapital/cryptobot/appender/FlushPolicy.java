package com.mistrycapital.cryptobot.appender;

enum FlushPolicy {
	/** Do not flush on each write (for log files that require speed) */
	DONT_FLUSH,
	/** Flush on each write to guarantee we store the data on a crash */
	FLUSH_EACH_WRITE
}
