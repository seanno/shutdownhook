/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.shl;

import java.io.IOException;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

public class App 
{
	public static class Config
	{
		public WebServer.Config Server;
		public SHL.Config SHL;

		public String ManifestUrl = "/manifest";
		public String ContentUrl = "/content";
		public String CreateUrl = "/create";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public static void main(String[] args) throws Exception {
		Easy.setSimpleLogFormat("INFO");
		runServer(args[0]);
	}

	private static void runServer(String cfgPath) throws Exception {
		
		final Config cfg = Config.fromJson(Easy.stringFromSmartyPath(cfgPath));
		final WebServer server = WebServer.create(cfg.Server);
		final SHL shl = new SHL(cfg.SHL);
			
		registerManifestHandler(cfg.ManifestUrl, server, shl);
		registerContentHandler(cfg.ContentUrl, server, shl);
		registerCreateHandler(cfg.CreateUrl, server, shl);

		server.registerEmptyHandler("/favicon.ico", 404);
		
		server.runSync();
	}

	private static void registerManifestHandler(String url,
												WebServer server,
												SHL shl) throws IOException {
		
		server.registerHandler(url, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {
				// nyi
			}
		});
	}

	private static void registerContentHandler(String url,
											   WebServer server,
											   SHL shl) throws IOException {
		
		server.registerHandler(url, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {
				// nyi
			}
		});
	}

	private static void registerCreateHandler(String url,
											  WebServer server,
											  SHL shl) throws IOException {
		
		server.registerHandler(url, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {
				// nyi
			}
		});
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
