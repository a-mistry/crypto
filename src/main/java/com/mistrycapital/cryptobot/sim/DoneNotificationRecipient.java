package com.mistrycapital.cryptobot.sim;

/** Used to pass a message indicating the previous step is completely finished */
public interface DoneNotificationRecipient {
	/** Note that there are no more messages to process beyond what is in the queue */
	void markDone();
}
