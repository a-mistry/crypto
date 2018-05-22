package com.mistrycapital.cryptobot.book;

import com.mistrycapital.cryptobot.gdax.websocket.*;
import com.mistrycapital.cryptobot.gdax.common.Product;
import com.mistrycapital.cryptobot.time.TimeKeeper;

public class OrderBookManager implements GdaxMessageProcessor {
	private OrderBook[] orderBooks;
	private GdaxMessageProcessor[] bookProcessors;
	
	public OrderBookManager(final TimeKeeper timeKeeper) {
		orderBooks = new OrderBook[Product.count];
		bookProcessors = new GdaxMessageProcessor[Product.count];
		for(Product product : Product.FAST_VALUES) {
			int index = product.getIndex();
			orderBooks[index] = new OrderBook(timeKeeper, product);
			bookProcessors[index] = orderBooks[index].getBookProcessor();
		}
	}
	
	public OrderBook getBook(Product product) {
		return orderBooks[product.getIndex()];
	}

	@Override
	public void process(Book msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(Received msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(Open msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(Done msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(Match msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(ChangeSize msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(ChangeFunds msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}

	@Override
	public void process(Activate msg) {
		bookProcessors[msg.getProduct().getIndex()].process(msg);
	}
}