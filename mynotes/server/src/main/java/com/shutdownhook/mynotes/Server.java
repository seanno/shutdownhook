/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.mynotes;

import java.io.Closeable;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();
		public String LoggingConfigPath = "@logging.properties";

		public String ClientSiteZip = "@clientSite.zip";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;

		if (cfg.WebServer.StaticPagesDirectory == null) {
			this.cfg.WebServer.StaticPagesZip = cfg.ClientSiteZip;
			this.cfg.WebServer.StaticPagesRouteHtmlWithoutExtension = false;
		}

		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {
		server = WebServer.create(cfg.WebServer);
		// nyi routes
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
