/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

import com.google.gson.Gson;

public class Server
{
	// +-------+
	// | Setup |
	// +-------+

	public static class Config
	{
		public WebServer.Config WebServer;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Server(Config cfg) throws Exception
	{
		this.cfg = cfg;
		this.webServer = WebServer.create(cfg.WebServer);
	}

	public static void runSync(Config cfg) throws Exception {
		new Server(cfg).webServer.runSync();
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer webServer;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
