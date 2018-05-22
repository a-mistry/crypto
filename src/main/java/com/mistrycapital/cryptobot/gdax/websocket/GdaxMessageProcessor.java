package com.mistrycapital.cryptobot.gdax.websocket;

public interface GdaxMessageProcessor {
	void process(Book msg);
	void process(Received msg);
	void process(Open msg);
	void process(Done msg);
	void process(Match msg);
	void process(ChangeSize msg);
	void process(ChangeFunds msg);
	void process(Activate msg);
}