/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Ignore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import com.shutdownhook.toolbox.Global;
import com.shutdownhook.toolbox.discovery.Wsd.WsdServiceInfo;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfo;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfoHandler;

public class WsdTest
{
	private static final String WSD_ID = "urn:uuid:" + UUID.randomUUID().toString();
	private static final String WSA_NAMESPACE = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
	
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
		DatagramSocket sock = new DatagramSocket();
		ConcurrentHashMap<String,ServiceInfo> infos = new ConcurrentHashMap<String,ServiceInfo>();
		
		Wsd wsd = new Wsd(new Wsd.Config());
		
		wsd.go(new ServiceInfoHandler() {

			public void search(ServiceInfo info) {

				System.out.println("TEST/SEARCH: " + info.toString());
				
				String relatesTo = ((WsdServiceInfo)info).XmlDoc
					.getElementsByTagNameNS(WSA_NAMESPACE, "MessageID")
					.item(0).getTextContent();
										
				String msg = String.format(RESPONSE_FMT,
										   UUID.randomUUID(), // message id
										   relatesTo, // incoming id
										   WSD_ID);
										   
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
			Thread.sleep(2000);
			Assert.assertTrue(infos.containsKey(WSD_ID));
		}
		finally {
			sock.close();
			wsd.close();
		}
	}

	private final static String RESPONSE_FMT =
		"<?xml version=\"1.0\" encoding=\"utf-8\"?> " +
		"<soap:Envelope " +
		"  xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" " +
		"  xmlns:wsdp=\"http://schemas.xmlsoap.org/ws/2006/02/devprof\" " +
		"  xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
		"  xmlns:wsd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
		"  xmlns:wprt=\"http://schemas.microsoft.com/windows/2006/08/wdp/print\" " +
		"  xmlns:wprt20=\"http://schemas.microsoft.com/windows/2014/04/wdp/printV20\" " +
		"  xmlns:wscn=\"http://schemas.microsoft.com/windows/2006/08/wdp/scan\"> " +
	    "  <soap:Header> " +
		"    <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action> " +
		"    <wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To> " +
		"    <wsa:MessageID>urn:uuid:%s</wsa:MessageID> " +
		"    <wsa:RelatesTo>%s</wsa:RelatesTo> " +
		"    <wsd:AppSequence InstanceId=\"46\" MessageNumber=\"230\"></wsd:AppSequence> " +
		"  </soap:Header> " +
		"  <soap:Body> " +
		"    <wsd:ProbeMatches> " +
		"      <wsd:ProbeMatch> " +
		"        <wsa:EndpointReference> " +
		"          <wsa:Address>%s</wsa:Address> " +
		"        </wsa:EndpointReference> " +
		"        <wsd:Types>wsdp:Device wscn:ScanDeviceType wprt:PrintDeviceType</wsd:Types> " +
		"        <wsd:XAddrs>http://127.0.0.1/bananafishbones</wsd:XAddrs> " +
		"        <wsd:MetadataVersion>22</wsd:MetadataVersion> " +
		"      </wsd:ProbeMatch> " +
		"    </wsd:ProbeMatches> " +
		"  </soap:Body> " +
		"</soap:Envelope>";
}
