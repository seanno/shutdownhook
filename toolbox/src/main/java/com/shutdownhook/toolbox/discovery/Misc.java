/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.net.InetAddress;
import java.util.Scanner;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.discovery.UdpServiceDiscovery.Config;
import com.shutdownhook.toolbox.discovery.UdpServiceDiscovery.DiscoveryHandler;


public class Misc
{
	public static void main(String[] args) throws Exception {

		if (args.length < 3 || args.length > 4) {
			System.err.println("Args = ADDRESS PORT DISCOVERY_MSG");
			System.err.println("(or)   ADDRESS PORT path DISCOVERY_PATH");
			return;
		}
		
		Easy.setSimpleLogFormat("INFO");

		Config cfg = new Config();
		cfg.Address = args[0];
		cfg.Port = Integer.parseInt(args[1]);
		cfg.UnicastResponsesOnly = true;
		
		final String msg = (args[2].equalsIgnoreCase("path")
		    ? Easy.stringFromSmartyPath(args[3]) : args[2]);

		UdpServiceDiscovery disco = new UdpServiceDiscovery(cfg);
		
		disco.go(new DiscoveryHandler() {
			public String getDiscoveryMessage() {
				return(msg);
			}
				
			public void receiveMessage(String msg, InetAddress addr, int port) {
				System.out.println(String.format("============ %s:%d\n%s\n",
												 addr, port, msg.trim()));
			}
		});

		try {
			System.out.println("Enter to resend discovery request, ^C to exit...");
			Scanner s = new Scanner(System.in);
			for (;;) {
				s.nextLine();
				disco.discover();
			}
		}
		finally {
			disco.close();
		}
	}

	private final static Logger log = Logger.getLogger(Misc.class.getName());
}
