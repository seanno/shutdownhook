/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.IOException;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

public class App 
{
	public static class Config
	{
		public SearchController.Config Controller;
		public WebServer.Config Server;

		public String LoggingConfigPath = "@logging.properties";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public static void main(String[] args) throws Exception {

		Config cfg = Config.fromJson(Easy.stringFromSmartyPath(args[0]));
		Easy.configureLoggingProperties(cfg.LoggingConfigPath);

		WebServer server = WebServer.create(cfg.Server);
		server.registerEmptyHandler("/favicon.ico", 404);
		registerSearchHandler(server, cfg);
		server.runSync();
	}

	public static void registerSearchHandler(final WebServer server, final Config cfg)
		throws Exception {

		final SearchController controller = new SearchController(cfg.Controller);
		final Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		server.registerHandler("/search", new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String input = request.QueryParams.get("input");
				RokuSearchInfo info = controller.parse(input);
					
				response.ContentType = "text/plain";
				response.Body = info.toString();
			}
		});
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
