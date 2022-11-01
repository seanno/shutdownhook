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

import com.shutdownhook.s2rsvc.wiki.WikiShows;
import com.shutdownhook.s2rsvc.tvdb.Lookup;

public class App 
{
	public static class Config
	{
		public SearchParser.Config Parser;
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

		final SearchParser parser = new SearchParser(cfg.Parser);
		final Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		server.registerHandler("/search", new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String input = request.QueryParams.get("input");
				SearchParser.ParsedSearch srch = parser.parse(input);
					
				response.ContentType = "text/plain";
				response.Body = srch.toString();
			}
		});
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
