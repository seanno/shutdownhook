/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

abstract public class ServiceDiscovery extends UdpServiceDiscovery
{
	// +--------------------+
	// | ServiceInfoHandler |
	// +--------------------+

	public static class ServiceInfo
	{
		public String Id;
		public String Url;
		public String Types;

		public InetAddress SourceAddress;
		public Integer SourcePort;

		@Override
		public String toString() {
			return(String.format("%s | %s | %s | (%s:%d)",
								 Id, Types, Url, SourceAddress, SourcePort));
		}
	}
	
	public interface ServiceInfoHandler {
		default public void alive(ServiceInfo info) { }
		default public void gone(ServiceInfo info) { }
		default public void search(ServiceInfo info) { }
		default public void other(ServiceInfo info) { }
		default public void discovering() { }
	}

	// +--------------+
	// | Implement Me |
	// +--------------+

	public enum MessageType {
		ALIVE,
		GONE,
		SEARCH,
		OTHER
	}

	public static class ParsedMessage
	{
		public MessageType Type;
		public ServiceInfo Info;
	}
	
	protected abstract String getDiscoveryMessage();
	
	protected abstract ParsedMessage parseIncomingMessage(String msg,
														  InetAddress addr,
														  int port);

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public ServiceDiscovery(Config cfg) {
		super(cfg);
	}

	public void go(ServiceInfoHandler handler) throws IOException {

		final ServiceDiscovery sd = this;
		
		this.go(new UdpServiceDiscovery.DiscoveryHandler() {

			public String getDiscoveryMessage() {
				return(sd.getDiscoveryMessage());
			}
				
			public void receiveMessage(String msg, InetAddress addr, int port) {
				
				ParsedMessage pm = sd.parseIncomingMessage(msg, addr, port);

				if (pm == null) return;
				
				switch (pm.Type) {
				    case ALIVE: handler.alive(pm.Info); break;
				    case GONE: handler.gone(pm.Info); break;
				    case SEARCH: handler.search(pm.Info); break;
				    case OTHER: handler.other(pm.Info); break;
				}
			}

			public void discovering() {
				handler.discovering();
			}
		});
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");
		
		final boolean ssdp =
			(args.length == 0 || args[0].equalsIgnoreCase("ssdp"));

		ServiceDiscovery discovery =
			(ssdp ? new Ssdp(new Ssdp.Config()) : new Wsd(new Wsd.Config()));

		discovery.go(new ServiceInfoHandler() {
			public void alive(ServiceInfo info) {
				System.out.println("+++ ALIVE:\t" + info.toString());
			}
			public void gone(ServiceInfo info) {
				System.out.println("--- GONE:\t" + info.toString());
			}
			public void search(ServiceInfo info) {
				System.out.println("<<< SEARCH:\t" + info.toString());
			}
			public void other(ServiceInfo info) {
				System.out.println("??? OTHER:\t" + info.toString());
			}
			public void discovering() {
				System.out.println(">>> DISCOVERING");
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
	
	private final static Logger log =
		Logger.getLogger(ServiceDiscovery.class.getName());
}
