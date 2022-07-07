/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;		
import java.util.logging.Logger;

public class SimpleServiceDiscovery implements Closeable
{
	// +-----------------------------------+
	// | ServiceInfo & NotificationHandler |
	// +-----------------------------------+

	public static class ServiceInfo
	{
		public String ServiceType;
		public String UniqueServiceName;
		public String Location;
		public Instant Expires;

		public Map<String,String> All;
		
		public InetAddress MessageSourceAddress;
		public Integer MessageSourcePort;

		public boolean isExpired() {
			return(Expires != null && Expires.isBefore(Instant.now()));
		}

		@Override
		public String toString() {
			return(String.format("ServiceType: %s | Location: %s | " +
								 "Expires: %s | Source: %s:%d",
								 ServiceType, Location, Expires,
								 MessageSourceAddress, MessageSourcePort));
		}
	}

	public interface NotificationHandler
	{
		default public void alive(ServiceInfo info) { }
		default public void gone(ServiceInfo info) { }
		default public void msearch(ServiceInfo info) { }
		default public void discovering() { }
	}

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Search = SSDP_ALL;
		public Integer MxDelaySeconds = 4;
			
		public String Address = "239.255.255.250";
		public Integer Port = 1900;

		public Integer DiscoveryIntervalSeconds = 60 * 20; // 0 == no auto-discovery

		public Integer StopWaitSeconds = 10;
		public Integer ReceiveTimeoutSeconds = 1;
	}

	public SimpleServiceDiscovery(Config cfg) {
		this.cfg = cfg;
		this.discoveryFlag = new AtomicBoolean(true);
	}

	public void go(NotificationHandler handler) throws IOException {

		this.handler = handler;

		this.notificationWorker = new SsdpWorker(this, SsdpSocketType.NOTIFICATION);
	    this.discoveryWorker = new SsdpWorker(this, SsdpSocketType.DISCOVERY);
		
		log.info("Starting worker threads...");
		
		this.notificationWorker.go();
		this.discoveryWorker.go();
	}

	public void close() {

		if (discoveryWorker == null) return;
		
		log.info("Signaling stop for worker threads...");
		
		notificationWorker.signalStop();
		discoveryWorker.signalStop();
		
		notificationWorker.waitForStop(cfg.StopWaitSeconds);
		discoveryWorker.waitForStop(cfg.StopWaitSeconds);
	}

	// +------------------------+
	// | DiscoveryWorker Thread |
	// +------------------------+

	public void discover() {
		discoveryFlag.set(true);
	}

	public enum SsdpSocketType
	{
		DISCOVERY,
		NOTIFICATION
	}

	public static class SsdpWorker extends Worker
	{
		private final SimpleServiceDiscovery svc;
		private final SsdpSocketType type;

		private MulticastSocket sock;
		private AtomicBoolean discoveryFlag;
		private Instant nextDiscovery;

		byte[] rgb;
		DatagramPacket dgram;

		public SsdpWorker(SimpleServiceDiscovery svc,
						  SsdpSocketType type) throws IOException{
			
			this.svc = svc;
			this.type = type;
			
			rgb = new byte[1024];
			dgram = new DatagramPacket(rgb, rgb.length);

			if (type.equals(SsdpSocketType.DISCOVERY)) {

				this.sock = new MulticastSocket();
				this.nextDiscovery = Instant.MIN;
				this.discoveryFlag = new AtomicBoolean(true);
			}
			else {
				this.sock = new MulticastSocket(svc.cfg.Port);
			}
		}

		@Override
		public void cleanup(Exception e) {
			sock.close();
		}
		
		// +------+
		// | work |
		// +------+

		@Override
		public void work() throws Exception {

			try {
				log.info(String.format("Starting SsdpWorker (%s)...", type));

				sock.setSoTimeout(svc.cfg.ReceiveTimeoutSeconds * 1000);
				sock.setReuseAddress(true);

				if (type.equals(SsdpSocketType.NOTIFICATION)) {
					joinGroups(sock);
				}

				while (!shouldStop()) {
					try {
						maybeDiscover(sock);
						sock.receive(dgram);
						svc.handleMessage(dgram);
					}
					catch (SocketTimeoutException te) {
						// just return
					}
					catch (Exception e) {
						log.info(String.format("Recv exception in SsdpWorker (%s): %s",
											   type, e.toString()));
					}
				}
			}
			catch (Exception eOuter) {
				log.info(String.format("Outer exception in SsdpWorker (%s): %s",
									   type, eOuter.toString()));
			}
			finally {
				log.info(String.format("Exiting SsdpWorker (%s)...", type));
			}
		}

		private void maybeDiscover(MulticastSocket sock) throws IOException {
			
			if (!needDiscovery()) return;

			discoveryFlag.set(false);
			nextDiscovery = Instant.now().plusSeconds(svc.cfg.DiscoveryIntervalSeconds);
			
			discoverNow(sock);
		}
		
		private boolean needDiscovery() {
			if (!type.equals(SsdpSocketType.DISCOVERY)) return(false);
			if (discoveryFlag.get()) return(true);
			if (svc.cfg.DiscoveryIntervalSeconds == 0) return(false);
			return(nextDiscovery.isBefore(Instant.now()));
		}
	
		private void joinGroups(MulticastSocket sock) throws IOException {

			final InetSocketAddress group =
				new InetSocketAddress(svc.cfg.Address, svc.cfg.Port);
			
			forEachUsefulInterface(new UsefulInterfaceCallback() {
				public void handle(NetworkInterface iface) throws IOException {
					log.fine("Joining multicast group on " + iface.getName());
					sock.joinGroup(group, iface);
				}
			});
		}

		// +-------------+
		// | discoverNow |
		// +-------------+

		private final static String DISCOVER_MSG_FMT =
			"M-SEARCH * HTTP/1.1\r\n" +
			"Host: %s:%d\r\n" +
			"Man: \"ssdp:discover\"\r\n" +
			"ST: %s\r\n" +
			"Mx: %d\r\n" +
			"\r\n";

		private void discoverNow(final MulticastSocket sock)

			throws IllegalArgumentException,
				   IllegalBlockingModeException, 
				   IOException,
				   PortUnreachableException,
				   SecurityException,
				   SocketException {

			String msg = String.format(DISCOVER_MSG_FMT, svc.cfg.Address, svc.cfg.Port,
									   svc.cfg.Search, svc.cfg.MxDelaySeconds);
		
			byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

			final DatagramPacket dgram =
				new DatagramPacket(rgb,
								   rgb.length,
								   InetAddress.getByName(svc.cfg.Address),
								   svc.cfg.Port);

			log.fine("Sending discover request: " + msg.replaceAll("\r\n", " | "));

			svc.handler.discovering();

			forEachUsefulInterface(new UsefulInterfaceCallback() {
				public void handle(NetworkInterface iface) throws IOException {
					log.fine("Sending discovery request on " + iface.toString());
					sock.setNetworkInterface(iface);
					sock.send(dgram);
				}
			});
		}

		// +------------------------+
		// | forEachUsefulInterface |
		// +------------------------+
		
		public interface UsefulInterfaceCallback {
			public void handle(NetworkInterface iface) throws Exception;
		}
	
		private void forEachUsefulInterface(UsefulInterfaceCallback callback)
			throws SocketException {

			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {

				NetworkInterface iface = ifaces.nextElement();
				
				if (iface.isLoopback() ||
					!iface.isUp() ||
					!iface.supportsMulticast()) continue;

				try {
					callback.handle(iface);
				}
				catch (Exception e) {
					log.info(String.format("FEUI Ex %s (%s)", iface, e));
				}
			}
		}
		
	}
		
	// +---------------+
	// | handleMessage |
	// +---------------+

	private void handleMessage(DatagramPacket dgram) {

		String msg = new String(dgram.getData(), StandardCharsets.UTF_8);
													  
		String[] lines = msg.split("\r\n");
		String line1 = lines[0].toLowerCase();

		ServiceInfo info = allocateServiceInfo(lines, dgram.getAddress(), dgram.getPort());

		// make sure this is an applicable message
		
		if (!cfg.Search.equals(SSDP_ALL) && !cfg.Search.equals(info.ServiceType)) {
			return;
		}

		if (line1.startsWith(CMD_MSEARCH)) {
			// M-SEARCH
			log.fine("m-search for " + info.ServiceType);
			handler.msearch(info);
		}
		else if (line1.startsWith(CMD_NOTIFY)) {
			// NOTIFY
			String nts = info.All.get("nts");
			if (SSDP_BYEBYE.equals(nts)) { 
				log.fine("ssdp.byebye for " + info.UniqueServiceName);
				handler.gone(info);
			}
			else if (SSDP_ALIVE.equals(nts)) {
				log.fine("ssdp.alive for " + info.UniqueServiceName);
				handler.alive(info);
			}
			else {
				log.fine("Unexpected NTS value for NOTIFY: " + nts);
			}
		}
		else if (line1.startsWith("http")) {
			// M-SEARCH Response
			log.fine("m-search response for " + info.UniqueServiceName);
			handler.alive(info);
		}
		else {
			log.info("Unexpected message: " + line1);
		}
	}

	private ServiceInfo allocateServiceInfo(String[] lines, InetAddress addr, int port) {

		ServiceInfo info = new ServiceInfo();
		info.All = new HashMap<String,String>();

		info.MessageSourceAddress = addr;
		info.MessageSourcePort = port;

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
					info.Location = val;
					break;

			    case "st":
			    case "nt":
					info.ServiceType = val;
					break;

			    case "usn":
					info.UniqueServiceName = val;
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

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");
		
		Config cfg = new Config();
		if (args.length > 0) cfg.Search = args[0];

		SimpleServiceDiscovery ssdp = new SimpleServiceDiscovery(cfg);

		ssdp.go(new NotificationHandler() {
			public void alive(ServiceInfo info) {
				System.out.println(">>> ALIVE:\n" + info.toString());
			}
					
			public void gone(ServiceInfo info) {
				System.out.println("<<< GONE:\n" + info.toString());
			}
		});

		try {
			System.out.println("Enter to resend discovery request, ^C to exit...");
			Scanner s = new Scanner(System.in);
			for (;;) {
				s.nextLine();
				ssdp.discover();
			}
		}
		finally {
			ssdp.close();
		}
	}

	// +---------------------+
	// | Members & Constants |
	// +---------------------+
	
	final private Config cfg;
	final private AtomicBoolean discoveryFlag;

	private NotificationHandler handler;
	private SsdpWorker notificationWorker;
	private SsdpWorker discoveryWorker;
	
	private final static String MAX_AGE = "max-age";
	
	private final static String CMD_MSEARCH = "m-search";
	private final static String CMD_NOTIFY = "notify";
	
	private final static String SSDP_ALL = "ssdp:all";
	private final static String SSDP_BYEBYE = "ssdp:byebye";
	private final static String SSDP_ALIVE = "ssdp:alive";
	
	private final static Logger log =
		Logger.getLogger(SimpleServiceDiscovery.class.getName());
}
