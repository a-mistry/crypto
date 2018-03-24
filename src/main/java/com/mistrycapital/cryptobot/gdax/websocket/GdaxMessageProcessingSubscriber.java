package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import org.slf4j.Logger;

import com.mistrycapital.cryptobot.util.MCLoggerFactory;

public class GdaxMessageProcessingSubscriber implements Subscriber<GdaxMessage> {
	private static final Logger log = MCLoggerFactory.getLogger();
	
	private final GdaxMessageProcessor processor;
	private Subscription subscription;
	
	GdaxMessageProcessingSubscriber(final GdaxMessageProcessor processor) {
		this.processor = processor;
	}

	@Override
	public void onComplete() {
	}

	@Override
	public void onError(Throwable e) {
		log.error("", e);
	}

	@Override
	public void onNext(GdaxMessage msg) {
		msg.process(processor);
		subscription.request(1);
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.subscription = subscription;
		subscription.request(1);
	}
}