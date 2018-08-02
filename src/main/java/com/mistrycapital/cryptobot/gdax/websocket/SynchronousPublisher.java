package com.mistrycapital.cryptobot.gdax.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

class SynchronousPublisher<T> implements Publisher<T> {
	private final List<Subscriber<? super T>> subscribers = new ArrayList<>();

	@Override
	public void subscribe(final Subscriber<? super T> subscriber) {
		subscribers.add(subscriber);
		subscriber.onSubscribe(new DummySubscription());
	}

	void submit(final T message) {
		for(int i = 0; i < subscribers.size(); i++) {
			subscribers.get(i).onNext(message);
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
