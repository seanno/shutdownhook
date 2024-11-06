/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.mynotes;

import java.io.Closeable;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.WebRequests;

public class OpenAI implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Token;
		public WebRequests.Config WebRequests = new WebRequests.Config();

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public OpenAI(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.WebRequests);
	}
	
	public void close() {
		requests.close();
	}
	
	// +--------+
	// | Models |
	// +--------+

	public static class Request
	{
	}

	public static class Response
	{
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;

	private final static Logger log = Logger.getLogger(OpenAI.class.getName());
}
