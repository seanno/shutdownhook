/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;

import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ParsedMessage;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.MessageType;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfo;

import com.shutdownhook.toolbox.Easy;

public class Wsd extends ServiceDiscovery
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static final String WSD_ADDR = "239.255.255.250";
	public static final int WSD_PORT = 3702;

	public static class Config extends UdpServiceDiscovery.Config
	{
		Config() {
			Address = WSD_ADDR;
			Port = WSD_PORT;
		}
	}
	
	public Wsd(Config cfg) {
		super(cfg);
		this.cfg = cfg;
	}

	public class WsdServiceInfo extends ServiceInfo
	{
		Document XmlDoc;
	}
	
	// +--------------+
	// | Implement Me |
	// +--------------+

	protected String getDiscoveryMessage() {
		return(String.format(WSDISCOVERY_FMT, UUID.randomUUID().toString()));
	}
	
	protected ParsedMessage parseIncomingMessage(String msg,
												 InetAddress addr,
												 int port) {

		WsdServiceInfo info = new WsdServiceInfo();
		info.SourceAddress = addr;
		info.SourcePort = port;

		ParsedMessage pm = new ParsedMessage();
		pm.Info = info;

		try {
			info.XmlDoc = readXmlDoc(msg);

			NodeList nodes = info.XmlDoc.getElementsByTagNameNS(WSA_NAMESPACE, "Address");
			if (nodes.getLength() > 0) info.Id = nodes.item(0).getTextContent();

			nodes = info.XmlDoc.getElementsByTagNameNS(WSD_NAMESPACE, "XAddrs");
			if (nodes.getLength() > 0) info.Url = nodes.item(0).getTextContent();
				
			nodes = info.XmlDoc.getElementsByTagNameNS(WSD_NAMESPACE, "Types");
			if (nodes.getLength() > 0) info.Types = nodes.item(0).getTextContent();

			String action = info.XmlDoc.getElementsByTagNameNS(WSA_NAMESPACE, "Action")
				.item(0).getTextContent().toLowerCase();

			if (action.endsWith("/probematches") ||
				action.endsWith("/resolvematches") ||
				action.endsWith("/hello")) {
				
				pm.Type = MessageType.ALIVE;
			}
			else if (action.endsWith("/probe")) {
				pm.Type = MessageType.SEARCH;
			}
			else if (action.endsWith("/bye")) {
				pm.Type = MessageType.GONE;
			}
			else {
				pm.Type = MessageType.OTHER;
			}
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "Error parsing WSD XML", true));
			log.fine("XML IS:\n" + msg);
		}

		return(pm);
	}

	private Document readXmlDoc(String msg) throws Exception {

		ByteArrayInputStream input = null;
		Document xmlDoc = null;

		try {
			input = new ByteArrayInputStream(msg.trim().getBytes(StandardCharsets.UTF_8));
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			xmlDoc = factory.newDocumentBuilder().parse(input);
		}
		finally {
			try { if (input != null) input.close(); }
			catch (Exception e2) { /* eat it */ }
		}

		return(xmlDoc);
	}

	// +--------------------------------+
	// | Package-Local For Test / Mains |
	// +--------------------------------+

	protected static String getStandardProbe() {
		return(String.format(WSDISCOVERY_FMT, UUID.randomUUID().toString()));
	}

	// +---------------------+
	// | Members & Constants |
	// +---------------------+

	private final Config cfg;
	
	private final static String WSDISCOVERY_FMT =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
        "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" " +
		"    xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" " +
		"    xmlns:tns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
		"    xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\r\n" + 
        "   <soap:Header>\r\n" + 
        "      <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>\r\n" + 
        "      <wsa:MessageID>urn:uuid:%s</wsa:MessageID>\r\n" + 
        "      <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>\r\n" + 
        "   </soap:Header>\r\n" + 
        "   <soap:Body>\r\n" + 
        "      <tns:Probe>\r\n" + 
        "      </tns:Probe>\r\n" + 
        "   </soap:Body>\r\n" + 
        "</soap:Envelope>\r\n";

	private final static String WSA_NAMESPACE = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
	private final static String WSD_NAMESPACE =	"http://schemas.xmlsoap.org/ws/2005/04/discovery";

	private final static Logger log =
		Logger.getLogger(Wsd.class.getName());
}
