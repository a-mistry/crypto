module crypto {
	requires jdk.incubator.httpclient;
	requires slf4j.api;
	requires gson;
	requires websocket.api;
	requires websocket.client;
	requires jetty.util;
	requires commons.csv;
	requires jsr305;
	requires guava;
	// these 3 are for mysql
	requires mysql.connector.java;
	requires java.sql;
	requires java.naming;
	requires logback.classic;
	exports com.mistrycapital.cryptobot.gdax.websocket;
}