/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ParsedMessage;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.MessageType;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfo;

public class Ssdp extends ServiceDiscovery
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public final static String SSDP_ALL = "ssdp:all";
	public static final String SSDP_ADDR = "239.255.255.250";
	public static final int SSDP_PORT = 1900;

	public static class Config extends UdpServiceDiscovery.Config
	{
		public String Search = SSDP_ALL;
		public Integer MxDelaySeconds = 4;

		Config() {
			Address = SSDP_ADDR;
			Port = SSDP_PORT;
		}
	}
	
	public Ssdp(Config cfg) {
		super(cfg);
		this.cfg = cfg;
	}

	public class SsdpServiceInfo extends ServiceInfo
	{
		public Instant Expires;
		public Map<String,String> All;
	}
	
	// +--------------+
	// | Implement Me |
	// +--------------+

	protected String getDiscoveryMessage() {
		return(String.format(DISCOVER_MSG_FMT, cfg.Address, cfg.Port,
							 cfg.Search, cfg.MxDelaySeconds));
	}

	protected ParsedMessage parseIncomingMessage(String msg,
												 InetAddress addr,
												 int port) {

		String[] lines = msg.split("\r\n");
		String line1 = lines[0].toLowerCase();

		SsdpServiceInfo info = allocateServiceInfo(lines, addr, port);
		if (info == null) return(null);
		
		ParsedMessage pm = new ParsedMessage();
		pm.Info = info;

		if (!cfg.Search.equals(SSDP_ALL) && !cfg.Search.equals(info.Types)) {
			log.fine(String.format("Discarding ssdp msg for ST mismatch (%s/%s)",
								   cfg.Search, info.Types));
			return(null);
		}

		if (line1.startsWith(CMD_MSEARCH)) {
			// M-SEARCH
			log.fine("m-search for " + info.Types);
			pm.Type = MessageType.SEARCH;
		}
		else if (line1.startsWith(CMD_NOTIFY)) {
			// NOTIFY
			String nts = info.All.get("nts");
			if (SSDP_BYEBYE.equals(nts)) { 
				log.fine("ssdp.byebye for " + info.Id);
				pm.Type = MessageType.GONE;
			}
			else if (SSDP_ALIVE.equals(nts)) {
				log.fine("ssdp.alive for " + info.Id);
				pm.Type = MessageType.ALIVE;
			}
			else {
				log.fine("Unexpected NTS value for NOTIFY: " + nts);
				return(null);
			}
		}
		else if (line1.startsWith("http")) {
			// M-SEARCH Response
			log.fine("m-search response for " + info.Id);
			pm.Type = MessageType.ALIVE;
		}
		else {
			log.info("Unexpected message: " + line1);
			return(null);
		}

		return(pm);
	}

	private SsdpServiceInfo allocateServiceInfo(String[] lines,
												InetAddress addr,
												int port) {

		SsdpServiceInfo info = new SsdpServiceInfo();
		
		info.All = new HashMap<String,String>();
		
		info.SourceAddress = addr;
		info.SourcePort = port;
		
		for (int i = 1; i < lines.length; ++i) {
			
			String trimmed = lines[i].trim();
			if (trimmed.isEmpty()) break;

			int ichColon, ichValue;
			int cch = trimmed.length();
			
			for (ichColon = 0; ichColon < cch; ++ichColon) {
				if (trimmed.charAt(ichColon) == ':') break;
			}

			if (ichColon == cch) break;

			for (ichValue = ichColon + 1; ichValue < cch; ++ichValue) {
				if (!Character.isWhitespace(trimmed.charAt(ichValue))) break;
			}

			String hdr = trimmed.substring(0, ichColon).toLowerCase();
			String val = trimmed.substring(ichValue);
			info.All.put(hdr, val);

			switch (hdr) {
				
			    case "location":
					info.Url = val;
					break;

			    case "st":
			    case "nt":
					info.Types = val;
					break;

			    case "usn":
					info.Id = val;
					break;

			    case "cache-control":
					info.Expires = parseFromMaxAge(val);
				    break;

			    case "expires":
					ZonedDateTime zdt =
						ZonedDateTime.parse(val, DateTimeFormatter.RFC_1123_DATE_TIME);
					
					info.Expires = zdt.toInstant();
				    break;
			}
		}

		return(info);
	}

	private Instant parseFromMaxAge(String val) {

		int cch = val.length();
		
		int ich = val.toLowerCase().indexOf(MAX_AGE);
		if (ich == -1) return(null);

		ich += MAX_AGE.length();
		while (ich < cch && !Character.isDigit(val.charAt(ich))) ++ich;
		if (ich == cch) return(null);

		int age = 0;
		while (ich < cch) {
			char ch = val.charAt(ich++);
			if (!Character.isDigit(ch)) break;

			age *= 10;
			age += Character.getNumericValue(ch);
		}

		return(age == 0 ? null : Instant.now().plusSeconds(age));
	}

	// +--------------------------------+
	// | Package-Local For Test / Mains |
	// +--------------------------------+

	protected static String getStandardProbe() {
		return(String.format(DISCOVER_MSG_FMT, SSDP_ADDR, SSDP_PORT, SSDP_ALL, 4));
	}

	// +---------------------+
	// | Members & Constants |
	// +---------------------+

	private final Config cfg;
	
	private final static String DISCOVER_MSG_FMT =
		"M-SEARCH * HTTP/1.1\r\n" +
		"Host: %s:%d\r\n" +
		"Man: \"ssdp:discover\"\r\n" +
		"ST: %s\r\n" +
		"Mx: %d\r\n" +
		"\r\n";

	private final static String MAX_AGE = "max-age";
	
	private final static String CMD_MSEARCH = "m-search";
	private final static String CMD_NOTIFY = "notify";
	
	private final static String SSDP_BYEBYE = "ssdp:byebye";
	private final static String SSDP_ALIVE = "ssdp:alive";

	private final static Logger log =
		Logger.getLogger(Ssdp.class.getName());
}
