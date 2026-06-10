//
// UTILITY.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.WebRequests;

public class Utility implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public WebRequests.Config Requests = new WebRequests.Config();
		public Integer ExecThreads = Exec.CACHED_THREADPOOL;
	}

	public Utility(Config cfg) throws Exception {
		this.cfg = cfg;
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.requests = new WebRequests(cfg.Requests);
		this.exec = new Exec(cfg.ExecThreads);
	}

	public void close() {
		requests.close();
		exec.close();
	}

	// +-----------+
	// | Utilities |
	// +-----------+

	public Gson getGson() { return(gson); }
	public WebRequests getRequests() { return(requests); }
	public Exec getExec() { return(exec); }

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Gson gson;
	private WebRequests requests;
	private Exec exec;
	
	private final static Logger log = Logger.getLogger(Utility.class.getName());
}
