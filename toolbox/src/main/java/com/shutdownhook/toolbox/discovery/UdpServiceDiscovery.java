/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;		
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Worker;

public class UdpServiceDiscovery implements Closeable
{
	// +------------------+
	// | DiscoveryHandler |
	// +------------------+

	public interface DiscoveryHandler {
		public String getDiscoveryMessage(); 
		public void receiveMessage(String msg, InetAddress addr, int port);
		default public void discovering() { }
	}

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Address = null;
		public Integer Port = null;

		public Integer DiscoveryIntervalSeconds = 60 * 20; // 0 == no auto-discovery
		public Boolean UnicastResponsesOnly = false; // true == don't listen for multicasts

		public Integer StopWaitSeconds = 10;
		public Integer ReceiveTimeoutSeconds = 1;

		public Integer DedupThresholdSeconds = 2; // 0 == no de-duping
		public Integer DedupMaxMessages = 100;
	}

	public UdpServiceDiscovery(Config cfg) {
		this.cfg = cfg;
		this.discoveryFlag = new AtomicBoolean(true);

		this.dedupLock = new Object();
		this.dedupHash = new HashSet<String>();
		this.dedupList = new LinkedList<DedupItem>();
	}

	public void go(DiscoveryHandler handler) throws IOException,
													IllegalArgumentException {

		if (cfg.Address == null || cfg.Port == null) {
			throw new IllegalArgumentException("Config requires Address and Port");
		}
		
		this.handler = handler;

		this.notificationWorker = new UdpWorker(this, SocketType.NOTIFICATION);
	    this.discoveryWorker = new UdpWorker(this, SocketType.DISCOVERY);
		
		log.info("Starting worker threads...");
		
		if (!cfg.UnicastResponsesOnly) this.notificationWorker.go();
		this.discoveryWorker.go();
	}

	public void close() {

		if (discoveryWorker == null) return;
		
		log.info("Signaling stop for worker threads...");
		
		notificationWorker.signalStop();
		discoveryWorker.signalStop();
		
		if (!cfg.UnicastResponsesOnly) notificationWorker.waitForStop(cfg.StopWaitSeconds);
		discoveryWorker.waitForStop(cfg.StopWaitSeconds);
	}

	// +------------------------+
	// | DiscoveryWorker Thread |
	// +------------------------+

	public void discover() {
		discoveryFlag.set(true);
	}

	public enum SocketType
	{
		DISCOVERY,
		NOTIFICATION
	}

	public static class UdpWorker extends Worker
	{
		private final UdpServiceDiscovery svc;
		private final SocketType type;

		private MulticastSocket sock;
		private Instant nextDiscovery;

		byte[] rgb;
		DatagramPacket dgram;

		public UdpWorker(UdpServiceDiscovery svc,
						 SocketType type) throws IOException{
			
			this.svc = svc;
			this.type = type;
			
			rgb = new byte[1024 * 20];
			dgram = new DatagramPacket(rgb, rgb.length);

			if (type.equals(SocketType.DISCOVERY)) {

				this.sock = new MulticastSocket();
				this.nextDiscovery = Instant.MIN;
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
				log.info(String.format("Starting UdpWorker (%s)...", type));

				sock.setSoTimeout(svc.cfg.ReceiveTimeoutSeconds * 1000);
				sock.setReuseAddress(true);
				sock.setLoopbackMode(false);

				if (type.equals(SocketType.NOTIFICATION)) {
					joinGroups(sock);
				}

				while (!shouldStop()) {
					try {
						maybeDiscover(sock);

						for (int ib = 0; ib < rgb.length; ++ib) rgb[ib] = 0;
						sock.receive(dgram);

						String msg = new String(dgram.getData(), StandardCharsets.UTF_8).trim();

						if (svc.isDuplicate(msg)) {
							log.fine("Skipping duplicate message");
							continue;
						}
						
						svc.handler.receiveMessage(msg, dgram.getAddress(), dgram.getPort());
					}
					catch (SocketTimeoutException te) {
						// all cool, just loop
					}
					catch (Exception e) {
						log.info(String.format("Recv exception in UdpWorker (%s): %s",
											   type, e.toString()));
					}
				}
			}
			catch (Exception eOuter) {
				log.info(String.format("Outer exception in UdpWorker (%s): %s",
									   type, eOuter.toString()));
			}
			finally {
				log.info(String.format("Exiting UdpWorker (%s)...", type));
			}
		}

		private void maybeDiscover(MulticastSocket sock) throws IOException {
			
			if (!needDiscovery()) return;

			svc.discoveryFlag.set(false);
			nextDiscovery = Instant.now()
				.plusSeconds(svc.cfg.DiscoveryIntervalSeconds);
			
			discoverNow(sock);
		}
		
		private boolean needDiscovery() {
			if (!type.equals(SocketType.DISCOVERY)) return(false);
			if (svc.discoveryFlag.get()) return(true);
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

		private void discoverNow(final MulticastSocket sock)

			throws IllegalArgumentException,
				   IllegalBlockingModeException, 
				   IOException,
				   PortUnreachableException,
				   SecurityException,
				   SocketException {

			String msg = svc.handler.getDiscoveryMessage(); 
			byte[] rgb = msg.getBytes(StandardCharsets.UTF_8);

			final DatagramPacket dgram =
				new DatagramPacket(rgb, rgb.length,
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
		
	// +--------------+
	// | Dedup Helper |
	// +--------------+

	public static class DedupItem
	{
		public String Hash;
		public Instant Expires;
	}
	
	private boolean isDuplicate(String msg) {

		if (cfg.DedupThresholdSeconds == 0) return(false);
		
		String hash = Easy.sha256(msg);
		Instant now = Instant.now();

		synchronized(dedupLock) {

			// get rid of expired messages
			while (dedupList.size() > 0 && dedupList.getLast().Expires.isBefore(now)) {
				dedupHash.remove(dedupList.getLast().Hash);
				dedupList.removeLast();
			}

			// return a hit 
			if (dedupHash.contains(hash)) {
				return(true);
			}

			// add the miss
			DedupItem item = new DedupItem();
			item.Hash = hash;
			item.Expires = now.plusSeconds(cfg.DedupThresholdSeconds);

			dedupHash.add(hash);
			dedupList.addFirst(item);

			// trim if needed
			if (dedupList.size() > cfg.DedupMaxMessages) {
				dedupHash.remove(dedupList.getLast().Hash);
				dedupList.removeLast();
			}

			return(false);
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");
		
		final boolean ssdp =
			(args.length == 0 || args[0].equalsIgnoreCase("ssdp"));

		Config cfg = new Config();
		cfg.Address = (ssdp ? Ssdp.SSDP_ADDR : Wsd.WSD_ADDR);
		cfg.Port = (ssdp ? Ssdp.SSDP_PORT : Wsd.WSD_PORT);

		UdpServiceDiscovery discovery = new UdpServiceDiscovery(cfg);

		discovery.go(new DiscoveryHandler() {
			public String getDiscoveryMessage() {
				return(ssdp ? Ssdp.getStandardProbe() : Wsd.getStandardProbe());
			}
				
			public void receiveMessage(String msg, InetAddress addr, int port) {
				System.out.println(String.format("============ %s:%d\n%s",
												 addr, port, msg.trim()));
			}
		});

		try {
			System.out.println("Enter to resend discovery request, ^C to exit...");
			Scanner s = new Scanner(System.in);
			for (;;) {
				s.nextLine();
				discovery.discover();
			}
		}
		finally {
			discovery.close();
		}
	}

	// +---------------------+
	// | Members & Constants |
	// +---------------------+
	
	final private Config cfg;
	final private AtomicBoolean discoveryFlag;

	private DiscoveryHandler handler;
	private UdpWorker notificationWorker;
	private UdpWorker discoveryWorker;

	private Object dedupLock;
	private Set<String> dedupHash;
	private LinkedList<DedupItem> dedupList;
	
	private final static Logger log =
		Logger.getLogger(UdpServiceDiscovery.class.getName());
}
