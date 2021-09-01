/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

// sadly echo.websocket.org shut down ... disabling this until I can cons up a replacement
@Ignore
public class TextWebSocketTest
{
	// +-------+
	// | Setup |
	// +-------+
	
	private static EchoReceiver receiver = null;
	private static TextWebSocket tws = null;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		// bless the folks hosting this service!
		TextWebSocket.Config cfg = new TextWebSocket.Config();
		cfg.LogContent = true;
		
		receiver = new EchoReceiver();
		tws = new TextWebSocket("wss://echo.websocket.org", cfg, receiver);
	}

	@AfterClass
	public static void afterClass() {
		tws.close();
		tws = null;
		receiver = null;
	}

	public static class EchoReceiver implements TextWebSocket.Receiver
	{
		public EchoReceiver() throws Exception {
			latch = new CountDownLatch(1);
		}

		public String waitForEcho() throws Exception {
			latch.await(5, TimeUnit.SECONDS);
			latch = new CountDownLatch(1);
			return(message);
		}

		public void receive(String message, TextWebSocket tws) throws Exception {
			this.message = message;
			latch.countDown();
		}

		public void error(Throwable error, TextWebSocket tws) throws Throwable {
			// shouldn't happen; fail test
			throw error;
		}

		String message;
		CountDownLatch latch;
	}

	// +-------+
	// | Tests |
	// +-------+

    @Test
    public void endToEnd() throws Exception {

		String msg = "TextWebSocketTest; random = " +
			Integer.toString(new Random().nextInt(20000));
		
		tws.send(msg);
		String received = receiver.waitForEcho();

		Assert.assertEquals(msg, received);
	}
}
