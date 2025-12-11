//
// EMAILER.JAVA
//

package com.shutdownhook.backstop;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;

public class Sender 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String ConnectionString;
		public String From;
		public String[] To;
		public int SendTimeoutMinutes = 5;
	}
	
	public Sender(Config cfg) {
		this.cfg = cfg;
	}

	// +------+
	// | send |
	// +------+

	public void send(String subject, String html) throws Exception {
		
		EmailClient client = new EmailClientBuilder()
			.connectionString(cfg.ConnectionString)
			.buildClient();

		EmailMessage msg = new EmailMessage()
			.setSenderAddress(cfg.From)
			.setToRecipients(cfg.To)
			.setSubject(subject)
			.setBodyHtml(html);

		EmailSendResult result = client
			.beginSend(msg)
			.waitForCompletion(Duration.ofMinutes(cfg.SendTimeoutMinutes))
			.getValue();

		if (result.getStatus() != EmailSendStatus.SUCCEEDED) {
			throw new Exception(String.format("Send failed: %s (%s) (id=%s)",
											  result.getError().getCode(),
											  result.getError().getMessage(),
											  result.getId()));
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
}
