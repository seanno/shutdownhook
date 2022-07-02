/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

public class SimpleServiceDiscoveryTest 
{
	private static final String SSDP_ST = "ssdp:toolbox_test";
	private static final String SSDP_USN = "urn:abcdefghijklmnopqrstuvwxyz";
	private static final String SSDP_LOCATION = "https://127.0.0.1/bananafishbones";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		Global.init();
	}

	@AfterClass
	public static void afterClass() {
		// nut-n-honey
	}

	@Test
    public void basicTest() throws Exception
    {
		SimpleServiceDiscovery.Config cfg = new SimpleServiceDiscovery.Config();
		cfg.Search = SSDP_ST;

		DatagramSocket sock = new DatagramSocket();
		
		SimpleServiceDiscovery ssdp =
			new SimpleServiceDiscovery(cfg, new SimpleServiceDiscovery.NotificationHandler() {

				public void msearch(SimpleServiceDiscovery.ServiceInfo info) {

					if (!SSDP_ST.equals(info.ServiceType)) return;
					
					String msg =
						"HTTP/1.0 200 OK\r\n" +
						"Cache-Control: max-age=1800\r\n" +
						"Location: " + SSDP_LOCATION + "\r\n" +
						"ST: " + SSDP_ST + "\r\n" +
						"USN: " + SSDP_USN + "\r\n" +
						"\r\n";

					byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

					DatagramPacket dgram =
						new DatagramPacket(rgb, rgb.length,
										   info.MessageSourceAddress,
										   info.MessageSourcePort);

					try {
						sock.send(dgram);
					}
					catch (Exception e) {
						Assert.fail(e.toString());
					}
				}

			});

		try {
			Thread.sleep(500);
			List<SimpleServiceDiscovery.ServiceInfo> infos = ssdp.get(SSDP_ST);
			Assert.assertEquals(1, infos.size());
		}
		finally {
			sock.close();
			ssdp.close();
		}
	}

}
