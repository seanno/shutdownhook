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
import java.util.Scanner;
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
	}

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static final int SSDP_PORT = 1900;
	public static final int WS_DISCOVERY_PORT = 3702;
	
	public static class Config
	{
		public String Address = "239.255.255.250";
		public Integer Port = SSDP_PORT;

		public Integer DiscoveryIntervalSeconds = 60 * 20; // 0 == no auto-discovery

		public Integer StopWaitSeconds = 10;
		public Integer ReceiveTimeoutSeconds = 1;
	}

	public UdpServiceDiscovery(Config cfg) {
		this.cfg = cfg;
		this.discoveryFlag = new AtomicBoolean(true);
	}

	public void go(DiscoveryHandler handler) throws IOException {

		this.handler = handler;

		this.notificationWorker = new UdpWorker(this, SocketType.NOTIFICATION);
	    this.discoveryWorker = new UdpWorker(this, SocketType.DISCOVERY);
		
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

				if (type.equals(SocketType.NOTIFICATION)) {
					joinGroups(sock);
				}

				while (!shouldStop()) {
					try {
						maybeDiscover(sock);

						for (int ib = 0; ib < rgb.length; ++ib) rgb[ib] = 0;
						sock.receive(dgram);

						System.out.println("MSG ON THREAD: " + type.toString());
						svc.handler.receiveMessage(
						    new String(dgram.getData(), StandardCharsets.UTF_8),
						    dgram.getAddress(), dgram.getPort());
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
		
	// +------------+
	// | Entrypoint |
	// +------------+

	private final static String SSDP_FMT =
		"M-SEARCH * HTTP/1.1\r\n" +
		"Host: 239.255.255.250:1900\r\n" +
		"Man: \"ssdp:discover\"\r\n" +
		"ST: ssdp:all\r\n" +
		"Mx: 4\r\n" +
		"X_ID: %s\r\n" +
		"\r\n";

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

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");
		
		final boolean ssdp =
			(args.length == 0 || args[0].equalsIgnoreCase("ssdp"));

		Config cfg = new Config();
		cfg.Port = (ssdp ? SSDP_PORT : WS_DISCOVERY_PORT);

		UdpServiceDiscovery discovery = new UdpServiceDiscovery(cfg);

		discovery.go(new DiscoveryHandler() {
			public String getDiscoveryMessage() {
				return(String.format(ssdp ? SSDP_FMT : WSDISCOVERY_FMT,
									 UUID.randomUUID().toString()));
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
	
	private final static Logger log =
		Logger.getLogger(UdpServiceDiscovery.class.getName());
}
