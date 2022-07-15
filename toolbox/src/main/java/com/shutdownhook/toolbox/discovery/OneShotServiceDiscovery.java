/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox.discovery;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfo;
import com.shutdownhook.toolbox.discovery.ServiceDiscovery.ServiceInfoHandler;


public class OneShotServiceDiscovery 
{
	public static enum ServiceType
	{
		SSDP,
		WSD
	}
	
	public static Set<ServiceInfo> search(ServiceType serviceType, int searchTimeSeconds) 
		throws IOException, InterruptedException {

		ServiceDiscovery disco = null;

		try {
			disco = (serviceType == ServiceType.SSDP
		        ? new Ssdp(new Ssdp.Config()) : new Wsd(new Wsd.Config()));
			
			return(search(disco, searchTimeSeconds));
		}
		finally {
			if (disco != null) disco.close();
		}
	}

	public static Set<ServiceInfo> search(ServiceDiscovery disco, int searchTimeSeconds)
		throws IOException, InterruptedException {

		final Set<ServiceInfo> infos = new HashSet<ServiceInfo>();

		disco.go(new ServiceInfoHandler() {
			public void alive(ServiceInfo info) {
				synchronized(infos) { infos.add(info); }
			}
		});

		Thread.sleep(searchTimeSeconds * 1000);

		return(infos);
	}
	
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");

		ServiceType serviceType = (args.length == 0 ? ServiceType.SSDP : ServiceType.valueOf(args[0]));
		int searchTimeSeconds = (args.length == 1 ? 8 : Integer.parseInt(args[1]));
			
		Set<ServiceInfo> infos = search(serviceType, searchTimeSeconds);
		for (ServiceInfo info : infos) System.out.println(info.toString());
	}
	

	// +---------------------+
	// | Members & Constants |
	// +---------------------+
	
	private final static Logger log =
		Logger.getLogger(OneShotServiceDiscovery.class.getName());
}
