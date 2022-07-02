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

public class SimpleServiceDiscovery extends Worker implements Closeable
{
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

		// the rfc says if we don't get Cache-Control or Expires headers then
		// we should never cache the result, but that is inane so we use this instead.
		public Integer DefaultExpiresSeconds = 60 * 60 * 24; 
 			
		public Integer StopWaitSeconds = 10;
		public Integer ReceiveTimeoutSeconds = 1;
	}

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
			return(String.format("ServiceType: %s\nLocation: %s\nExpires: %s\nSource: %s\n",
								 ServiceType, Location, Expires, MessageSourceAddress));
		}
	}

	public interface NotificationHandler
	{
		default public void alive(ServiceInfo info) { }
		default public void gone(ServiceInfo info) { }
		default public void msearch(ServiceInfo info) { }
	}

	public SimpleServiceDiscovery(Config cfg) throws IllegalArgumentException,
													 IOException {
		this(cfg, null);
	}

	public SimpleServiceDiscovery(Config cfg,
								  NotificationHandler handler)
		
		throws IllegalArgumentException,
			   IOException {

		this.cfg = cfg;
		this.handler = handler;
		this.discoveryFlag = new AtomicBoolean(true);
		this.services = new HashMap<String,ServiceInfo>();
		
		go();
	}

	public void close() {
		
		log.info("Signaling stop for SimpleServiceDiscovery thread...");
		signalStop();
		waitForStop(cfg.StopWaitSeconds);
	}

	// +----------------+
	// | Public Methods |
	// +----------------+

	public void discover() {
		discoveryFlag.set(true);
	}

	public List<ServiceInfo> get() {
		return(get(null));
	}
	
	public List<ServiceInfo> get(String serviceType) {

		removeExpired();

		List<ServiceInfo> infos = new ArrayList<ServiceInfo>();

		synchronized (servicesLock) {

			for (ServiceInfo info : services.values()) {
				
				if (serviceType == null ||
					SSDP_ALL.equals(serviceType) ||
					serviceType.equals(info.ServiceType)) {
					
					infos.add(info);
				}
			}
		}

		return(infos);
	}

	// +------------------+
	// | Discovery Thread |
	// +------------------+

	@Override
	public void work() throws Exception {
		
		final MulticastSocket sock = new MulticastSocket(cfg.Port);

		try {
			log.info("Starting SimpleServiceDiscovery thread...");

			sock.setSoTimeout(cfg.ReceiveTimeoutSeconds * 1000);
			sock.setReuseAddress(true);

			final InetSocketAddress group =	new InetSocketAddress(cfg.Address, cfg.Port);
			
			forEachUsefulInterface(new UsefulInterfaceCallback() {
				public void handle(NetworkInterface iface) throws IOException {
					log.fine("Joining multicast group on " + iface.getName());
					sock.joinGroup(group, iface);
				}
			});

			Instant lastDiscovery = Instant.MIN;
			Instant nextExpirationCheck = Instant.MAX;
			
			while (!shouldStop()) {

				if (needDiscovery(lastDiscovery)) {
					discoverNow(sock);
					discoveryFlag.set(false);
					lastDiscovery = Instant.now();

					// give the discovery time to complete, then remove
					// any that are expired that didn't come back.
					nextExpirationCheck =
						lastDiscovery.plusSeconds(cfg.MxDelaySeconds * 3);
				}

				if (nextExpirationCheck.isBefore(Instant.now())) {
					removeExpired();
					nextExpirationCheck = Instant.MAX;
				}

				byte[] rgb = new byte[1024];
				DatagramPacket dgram = new DatagramPacket(rgb, rgb.length);

				try {
					sock.receive(dgram);
					handleMessage(dgram);
				}
				catch (SocketTimeoutException te) {
					// loop
				}
				catch (Exception e) {
					log.fine("Exception in receive; looping: " + e.toString());
				}
			}
		}
		catch (Exception emain) {
			log.fine("Exception in worker: " + emain.toString());
		}
		finally {
			sock.close();
			log.info("Exiting SimpleServiceDiscovery thread...");
		}
	}

	private boolean needDiscovery(Instant lastDiscovery) {
		if (discoveryFlag.get()) return(true);
		if (cfg.DiscoveryIntervalSeconds == 0) return(false);
		return(lastDiscovery.plusSeconds(cfg.DiscoveryIntervalSeconds).isBefore(Instant.now()));
	}
	
	@Override
	public void cleanup(Exception e) {
		// nut-n-honey
	}

	private void handleMessage(DatagramPacket dgram) {

		String msg = new String(dgram.getData(), StandardCharsets.UTF_8);
													  
		String[] lines = msg.split("\r\n");
		String line1 = lines[0].toLowerCase();

		ServiceInfo info = allocateServiceInfo(lines, dgram.getAddress(), dgram.getPort());

		if (line1.startsWith(CMD_MSEARCH)) {
			// M-SEARCH
			log.fine("m-search for " + info.ServiceType);
			msearch(info);
		}
		else if (line1.startsWith(CMD_NOTIFY)) {
			// NOTIFY
			String nts = info.All.get("nts");
			if (SSDP_BYEBYE.equals(nts)) { 
				log.fine("ssdp.byebye for " + info.UniqueServiceName);
				byebye(info);
			}
			else if (SSDP_ALIVE.equals(nts)) {
				log.fine("ssdp.alive for " + info.UniqueServiceName);
				alive(info);
			}
			else {
				log.fine("Unexpected NTS value for NOTIFY: " + nts);
			}
		}
		else if (line1.startsWith("http")) {
			// M-SEARCH Response
			log.fine("m-search response for " + info.UniqueServiceName);
			alive(info);
		}
		else {
			log.info("Unexpected message: " + line1);
		}
	}

	private void msearch(ServiceInfo info) {
		
		if (handler != null &&
			(cfg.Search.equals(SSDP_ALL) || cfg.Search.equals(info.ServiceType))) {

			handler.msearch(info);
		}
	}

	private void byebye(ServiceInfo info) {
		
		boolean hadInfo = false;
		
		synchronized(servicesLock) {
			if (services.containsKey(info.UniqueServiceName)) {
				hadInfo = true;
				services.remove(info.UniqueServiceName);
			}
		}
		
		if (hadInfo && handler != null) handler.gone(info);
	}

	private void alive(ServiceInfo info) {
		synchronized(servicesLock) { services.put(info.UniqueServiceName, info); }
		if (handler != null) handler.alive(info);
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

		if (info.Expires == null) {
			info.Expires = Instant.now().plusSeconds(cfg.DefaultExpiresSeconds);
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

	// +---------------+
	// | removeExpired |
	// +---------------+

	private void removeExpired() {

		log.fine("Checking service expiration dates");
		
		synchronized (servicesLock) {

			List<String> removeList = new ArrayList<String>();

			for (ServiceInfo info : services.values()) {
				if (info.isExpired()) removeList.add(info.UniqueServiceName);
			}

			for (String usn : removeList) {
				log.fine("Expiring ServiceInfo for: " + usn);
				services.remove(usn);
			}
		}
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

		String msg = String.format(DISCOVER_MSG_FMT, cfg.Address, cfg.Port,
								   cfg.Search, cfg.MxDelaySeconds);

		log.fine("Sending discover request:\n" + msg.replaceAll("\r\n", " | "));
		
		byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

		final DatagramPacket dgram = new DatagramPacket(rgb, rgb.length,
														InetAddress.getByName(cfg.Address),
														cfg.Port);

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
			if (iface.isLoopback() || !iface.isUp() || !iface.supportsMulticast()) continue;

			try { callback.handle(iface); }
			catch (Exception e) { log.info(String.format("FEUI Ex %s (%s)", iface, e)); }
		}
	}
		
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");
		
		Config cfg = new Config();
		if (args.length > 0) cfg.Search = args[0];

		SimpleServiceDiscovery ssdp =
			new SimpleServiceDiscovery(cfg, new NotificationHandler() {
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
	final private NotificationHandler handler;

	final private Object servicesLock = new Object();
	final private Map<String,ServiceInfo> services;

	private final static String MAX_AGE = "max-age";
	
	private final static String CMD_MSEARCH = "m-search";
	private final static String CMD_NOTIFY = "notify";
	
	private final static String SSDP_ALL = "ssdp:all";
	private final static String SSDP_BYEBYE = "ssdp:byebye";
	private final static String SSDP_ALIVE = "ssdp:alive";
	
	private final static Logger log = Logger.getLogger(SimpleServiceDiscovery.class.getName());
}
