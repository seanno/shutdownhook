/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.io.Closeable;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
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
		public SqlStore.Config Sql = new SqlStore.Config();

		public String ListConnectionsUrl = "/connections";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		this.cfg = cfg;
		this.gson = new Gson();
		this.store = new QueryStore(cfg.Sql);
		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerListConnections();
		
		server.registerEmptyHandler("/favicon.ico", 404);
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +-------------------------+
	// | registerListConnections |
	// +-------------------------+

	private void registerListConnections() throws Exception {
		server.registerHandler(cfg.ListConnectionsUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {
				response.setJson(gson.toJson(store.listConnections(getAuthEmail(request))));
			}
		});
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static String getAuthEmail(Request request) throws Exception {
		String user = request.OAuth2.getEmail();
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email");
		return(user);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private QueryStore store;
	private Gson gson;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
