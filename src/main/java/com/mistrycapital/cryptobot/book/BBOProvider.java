package com.mistrycapital.cryptobot.book;

public interface BBOProvider {
	/**
	 * @return Top of book
	 */
	default BBO getBBO() {
		BBO bbo = new BBO();
		recordBBO(bbo);
		return bbo;
	}


	/**
	 * Records top of book to the given object
	 */
	void recordBBO(BBO bbo);
}
