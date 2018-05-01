package com.mistrycapital.cryptobot.twilio;

import com.mistrycapital.cryptobot.util.MCLoggerFactory;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;

public class TwilioSender {
	private static final Logger log = MCLoggerFactory.getLogger();

	private final PhoneNumber from;
	private final PhoneNumber to;

	public TwilioSender(String accountSid, String authToken) {
		Twilio.init(accountSid, authToken);
		from = new PhoneNumber("+16469923687");
		to = new PhoneNumber("+19144660063");
	}

	public void sendMessage(String msg) {
		Message message = Message.creator(to, from, msg).create();
		log.info("Send twilio msg id " + message.getSid() + ": " + message.getBody());
	}
}
