package com.mistrycapital.cryptobot.gdax.websocket;

import com.mistrycapital.cryptobot.gdax.common.Product;

public interface GdaxMessage {
	/** @return Type of message */
	Type getType();
	/** @return Product */
	Product getProduct();
	/** @return Microseconds since epoch */
	long getTimeMicros();
	/** @return Sequence number */
	long getSequence();
	/** Calls appropriate process message in the message processor to process this message */
	void process(GdaxMessageProcessor processor);
	
	enum Type {
		BOOK,
		OPEN,
		DONE,
		MATCH,
		CHANGE_SIZE,
		CHANGE_FUNDS,
		ACTIVATE,
		UNKNOWN
	}
}
