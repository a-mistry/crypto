package com.mistrycapital.cryptobot.gdax.websocket;

import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

class SynchronousPublisher<T> implements Publisher<T> {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final List<Subscriber<? super T>> subscribers = new ArrayList<>();

	@Override
	public void subscribe(final Subscriber<? super T> subscriber) {
		subscribers.add(subscriber);
		subscriber.onSubscribe(new DummySubscription());
	}

	void submit(final T message) {
		for(int i = 0; i < subscribers.size(); i++) {
			try {
				subscribers.get(i).onNext(message);
			} catch(Exception e) {
				log.error("Error thrown by SynchronousPublisher subscriber", e);
				try {
					subscribers.get(i).onError(e);
				} catch(Exception e2) {}
			}
		}
	}

	class DummySubscription implements Flow.Subscription {
		@Override
		public void request(final long n) {
			// nothing to do
		}

		@Override
		public void cancel() {
			throw new UnsupportedOperationException();
		}
	}
}
