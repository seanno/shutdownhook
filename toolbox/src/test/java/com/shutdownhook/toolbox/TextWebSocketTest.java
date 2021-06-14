/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

public class TextWebSocketTest
{
	// +-------+
	// | Setup |
	// +-------+
	
	private static EchoWebSocket echo = null;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		// bless the folks hosting this service!
		TextWebSocket.Config cfg = new TextWebSocket.Config();
		cfg.LogContent = true;
		echo = new EchoWebSocket("wss://echo.websocket.org", cfg);
	}

	@AfterClass
	public static void afterClass() {
		echo.close();
		echo = null;
	}

	public static class EchoWebSocket extends TextWebSocket
	{
		public EchoWebSocket(String url, TextWebSocket.Config cfg) throws Exception {
			super(url, cfg);
			latch = new CountDownLatch(1);
		}

		public String waitForEcho() throws Exception {
			latch.await(5, TimeUnit.SECONDS);
			latch = new CountDownLatch(1);
			return(message);
		}

		@Override
		public void receive(String message) throws Exception {
			this.message = message;
			latch.countDown();
		}

		@Override
		public void error(Throwable error) throws Throwable {
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
		
		echo.send(msg);
		String received = echo.waitForEcho();

		Assert.assertEquals(msg, received);
	}
}
