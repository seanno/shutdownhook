/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Ignore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import com.shutdownhook.toolbox.Global;
import com.shutdownhook.toolbox.discovery.Ssdp.SsdpServiceInfo;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfo;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfoHandler;

public class SsdpTest
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
	@Ignore
    public void basicTest() throws Exception
    {
		Ssdp.Config cfg = new Ssdp.Config();
		cfg.Search = SSDP_ST;

		DatagramSocket sock = new DatagramSocket();
		ConcurrentHashMap<String,ServiceInfo> infos = new ConcurrentHashMap<String,ServiceInfo>();
		
		Ssdp ssdp = new Ssdp(cfg);
		
		ssdp.go(new ServiceInfoHandler() {

			public void search(ServiceInfo info) {

				System.out.println("TEST/MSEARCH: " + info.toString());

				if (!SSDP_ST.equals(((SsdpServiceInfo)info).Types)) return;
					
				String msg =
					"HTTP/1.0 200 OK\r\n" +
					"Cache-Control: max-age=1800\r\n" +
					"Location: " + SSDP_LOCATION + "\r\n" +
					"ST: " + SSDP_ST + "\r\n" +
					"USN: " + SSDP_USN + "\r\n" +
					"\r\n";

				byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

				DatagramPacket dgram =
					new DatagramPacket(rgb, rgb.length, info.SourceAddress, info.SourcePort);

				try {
					sock.send(dgram);
				}
				catch (Exception e) {
					Assert.fail(e.toString());
				}
			}

			public void alive(ServiceInfo info) {
				System.out.println("TEST/ALIVE: " + info.toString());
				infos.put(info.Id, info);
			}

			public void gone(ServiceInfo info) {
				System.out.println("TEST/GONE: " + info.toString());
				infos.remove(info.Id);
			}

		});

		try {
			
			for (int cycle = 0; cycle < 6; ++cycle) {
				Thread.sleep(1000);
				if (infos.containsKey(SSDP_USN)) break;
				System.out.println(String.format("no ssdp key after %d seconds", cycle + 1));
			}
			
			Assert.assertTrue(infos.containsKey(SSDP_USN));
		}
		finally {
			sock.close();
			ssdp.close();
		}
	}

}
