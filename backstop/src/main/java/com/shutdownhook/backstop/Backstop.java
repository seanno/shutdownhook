//
// BACKSTOP.JAVA
//

package com.shutdownhook.backstop;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;
import com.shutdownhook.toolbox.Exec;

public class Backstop implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public final static int DEFAULT_THREADS = 10;
	
	public static class Config
	{
		public Resource.Config[] Resources;

		public int Threads = DEFAULT_THREADS;
		
		public static Config fromJson(String json) throws JsonSyntaxException {
			return(new Gson().fromJson(json, Backstop.Config.class));
		}
	}

	public Backstop(Config cfg) {
		this.cfg = cfg;
		this.exec = new Exec(cfg.Threads);
	}

	public void close() {
		exec.close();
	}

	public Config getConfig() {
		return(cfg);
	}
	
	// +----------+
	// | checkAll |
	// +----------+

	public List<Status> checkAll() throws Exception {

		// run all resource checkers async
		
		List<CompletableFuture<List<Status>>> futures =
			new ArrayList<CompletableFuture<List<Status>>>();

		for (int i = 0; i < cfg.Resources.length; ++i) {

			final Resource.Config resourceConfig = cfg.Resources[i];

			futures.add(exec.runAsync("Resource " + resourceConfig.Name,
									  new Exec.AsyncOperation() {

			    public List<Status> execute() throws Exception {
					return(checkOne(resourceConfig));
			    }
			}));
		}

		// collect up statuses
		
		List<Status> allStatus = new ArrayList<Status>();

		for (int i = 0; i < cfg.Resources.length; ++i) {
			allStatus.addAll(futures.get(i).get());
		}

		// sort and return
		Collections.sort(allStatus);

		return(allStatus);
	}
	
	// +----------+
	// | checkOne |
	// +----------+

	public List<Status> checkOne(final Resource.Config resourceConfig) throws Exception {
		final List<Status> statuses = new ArrayList<Status>();
		Resource.check(resourceConfig, statuses);
		if (statuses.size() == 0) statuses.add(new Status(resourceConfig));
		Collections.sort(statuses);
		return(statuses);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Exec exec;
}
