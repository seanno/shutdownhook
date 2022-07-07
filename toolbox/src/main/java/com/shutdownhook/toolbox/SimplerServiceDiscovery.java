/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.SimpleServiceDiscovery.NotificationHandler;
import com.shutdownhook.toolbox.SimpleServiceDiscovery.ServiceInfo;

// Just like SimpleServiceDiscovery except there are no notifications;
// instead you can just get a list of services at any time.

public class SimplerServiceDiscovery extends SimpleServiceDiscovery
{
	public SimplerServiceDiscovery(Config cfg) throws IOException {
		super(cfg);
		this.expirationGraceSeconds = cfg.MxDelaySeconds + 3;
		this.services = new HashMap<String, ServiceInfo>();
	}

	@Override
	public void go(NotificationHandler handler) throws IOException {

		final SimplerServiceDiscovery ssdp = this;

		super.go(new NotificationHandler() {
				
			public void alive(ServiceInfo info) {
				ssdp.alive(info);
				if (handler != null) handler.alive(info);
			}
				
			public void gone(ServiceInfo info) {
				ssdp.gone(info);
				if (handler != null) handler.gone(info);
			}
				
			public void discovering() {
				ssdp.discovering();
				if (handler != null) handler.discovering();
			}
		});
	}

	public List<ServiceInfo> get() { return(get(null)); }
	
	public synchronized List<ServiceInfo> get(String serviceType) {

		List<ServiceInfo> infos = new ArrayList<ServiceInfo>();

		boolean all = (serviceType == null || serviceType.equalsIgnoreCase("ssdp:all"));
		for (ServiceInfo info : services.values()) {
			if (all || serviceType.equals(info.ServiceType)) {
				infos.add(info);
			}
		}

		return(infos);
	}

	private synchronized void alive(ServiceInfo info) {
		services.put(info.UniqueServiceName, info);
	}

	private synchronized void gone(ServiceInfo info) {
		services.remove(info.UniqueServiceName);
	}

	private synchronized void discovering() {
		// the SSDP RFC says that if there is no cache-control or expires
		// header we should not cache the results. But that doesn't work in
		// this model, so instead we set it up so that each time we do
		// a new explicit discovery, we will expire out items that aren't
		// still responsive. Feels like a reasonable compromise

		for (ServiceInfo info : services.values()) {
			if (info.Expires == null) {
				info.Expires = Instant.now().plusSeconds(expirationGraceSeconds);
			}
		}
	}

	private int expirationGraceSeconds;
	private Map<String,ServiceInfo> services;

	private final static Logger log =
		Logger.getLogger(SimplerServiceDiscovery.class.getName());
}
