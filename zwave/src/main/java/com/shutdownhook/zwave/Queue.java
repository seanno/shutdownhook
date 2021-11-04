/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.zwave;

import java.io.Closeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Worker;

public class Queue extends Worker implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public String Url;
		public String Key;
		public String Secret;
		public String Region = "us-west-2";

		public Integer RequestTimeoutSeconds = 30; // must be > long poll time in queue cfg
		public Integer MaxIntervalSecondsForDup = 2; 
		public Integer StopWaitSeconds = 10;
	}

	public interface Handler {
		public void handle(String screenName, String screenSetting) throws Exception;
	}
	
	public Queue(Config cfg, Handler handler) throws Exception {
		this.cfg = cfg;
		this.handler = handler;
		this.parser = new JsonParser();

		createSqsClient();
		
		log.info("Starting queue thread...");
		go();
	}
	
	public void close() {
		log.info("Stopping queue thread...");
		signalStop();
		waitForStop(cfg.StopWaitSeconds);
	}

	// +--------+
	// | Worker |
	// +--------+

	@Override
	public void work() throws Exception {
		try {
			String lastScreenName = "";
			String lastScreenSetting = "";
			Instant lastReceived = Instant.now();

			// note this looks like a tight loop but isn't ... the queue is configured
			// for "long polling" which will wait up to 20 seconds for messages to appear.

			// each pass through this we get some messages (or maybe we don't) and handle
			// each within its own try/catch block. If the last message we saw is the same
			// as this one AND it's been a short enough period, we assume it's a dup message
			// and ignore it. If we get through the handler we delete the message, otherwise
			// we leave it in the queue which is configured to send messages to dead letter
			// after enough failed tries (3 at the time of this comment).
		 
			while (!shouldStop()) {

				List<Message> messages = null;
				
				try {
					messages = sqs.receiveMessage(cfg.Url).getMessages();
				}
				catch (Exception eRcv) {
					log.severe("Exception receiving messages; will wait 2 seconds: " + eRcv.toString());
					Thread.sleep(2000); // this is super-rare, try not to spin crazy on outage
					messages = new ArrayList<Message>();
				}

				if (messages.size() > 0) {
					log.info("Queue received " + Integer.toString(messages.size()) + " messsages");
				}
				else {
					log.fine("Queue received 0 messages");
				}
				
				for (Message m : messages) {
					try {
						JsonObject json = parser.parse(m.getBody()).getAsJsonObject();
						String screenName = json.get("screenName").getAsString();
						String screenSetting = json.get("screenSetting").getAsString();
						Instant received = Instant.now();

						if (lastScreenName.equals(screenName) &&
							lastScreenSetting.equals(screenSetting) &&
							received.isBefore(lastReceived.plusSeconds(cfg.MaxIntervalSecondsForDup))) {

							log.info(String.format("Ignoring dup %s/%s received at %s; original @ %s",
												   screenName, screenSetting, received, lastReceived));
						}
						else {
							lastScreenName = screenName;
							lastScreenSetting = screenSetting;
							lastReceived = received;

							log.info(String.format("Handling msg %s/%s", screenName, screenSetting));
							handler.handle(screenName, screenSetting);
						}
						
						sqs.deleteMessage(cfg.Url, m.getReceiptHandle());
					}
					catch (Exception eMsg) {
						log.severe("Exception handling message: " + Easy.exMsg(eMsg, "queue", true));
					}
				}
			}
		}
		catch (Exception e) {
			log.severe("Exception in queue thread; exiting: " + e.toString());
		}
	}
	
	@Override
	public void cleanup(Exception e) {
		// nut-n-honey
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private void createSqsClient() throws Exception {
		
		// see https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html
		System.setProperty("aws.accessKeyId", cfg.Key);
		System.setProperty("aws.secretKey", cfg.Secret);

		ClientConfiguration clientConfig = PredefinedClientConfigurations.defaultConfig();
		clientConfig.setRequestTimeout(cfg.RequestTimeoutSeconds * 1000);
		clientConfig.setConnectionTimeout(cfg.RequestTimeoutSeconds * 1000);

		sqs = AmazonSQSClientBuilder
			.standard()
			.withClientConfiguration(clientConfig)
			.withRegion(cfg.Region)
			.build();
	}
	
	private Config cfg;
	private Handler handler;
	private JsonParser parser;
	private AmazonSQS sqs;

	private final static Logger log = Logger.getLogger(Queue.class.getName());
}
