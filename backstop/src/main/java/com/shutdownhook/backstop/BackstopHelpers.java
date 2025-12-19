//
// BACKSTOPHELPERS.JAVA
//

package com.shutdownhook.backstop;

import java.io.Closeable;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BackstopHelpers implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public BackstopHelpers(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
		this.gson = new GsonBuilder().setPrettyPrinting().create();
	}

	public void close() {
		requests.close();
	}

	// +-----------+
	// | Accessors |
	// +-----------+

	public Gson getGson() { return(gson); }
	public WebRequests getRequests() { return(requests); }

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;
	private Gson gson;
}
