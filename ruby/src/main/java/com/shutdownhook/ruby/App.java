/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.ruby;

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
		public RuBy.Config Ruby;

		public String RubyUrl = "/ruby.js";
		public String RejectJSPath = "@reject.js";
		
		public String HomeUrl = "/";
		public String HomeHtmlPath = "@home.html";
		
		public Boolean AllowOverride = false;
		public String OverrideMarker = "x-rubyip";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat("INFO");

		switch (args[0]) {

		    case "i2a":
				System.out.println(RuBy.bigIntegerToAddress(args[1]));
				break;
				
		    case "a2i":
				System.out.println(RuBy.addressToBigInteger(args[1]));
				break;

		    default:
				runServer(args[0]);
				break;
		}
	}

	private static void runServer(String cfgPath) throws Exception {
		
		final Config cfg = Config.fromJson(Easy.stringFromSmartyPath(cfgPath));
		final WebServer server = WebServer.create(cfg.Server);

		registerRubyHandler(cfg, server);

		server.registerEmptyHandler("/favicon.ico", 404);
		
		String homeHtml = Easy.stringFromSmartyPath(cfg.HomeHtmlPath);
		server.registerStaticHandler(cfg.HomeUrl, homeHtml, "text/html");

		server.runSync();
	}

	private static void registerRubyHandler(Config cfg, WebServer server) throws IOException {
		
		final RuBy ruby = new RuBy(cfg.Ruby);
		final String rejectJS = Easy.stringFromSmartyPath(cfg.RejectJSPath);

		server.registerHandler(cfg.RubyUrl, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String ip = getClientIP(cfg, request);
				String js = "";
				
				try {
					if (ruby.inRange(ip)) js = rejectJS;
				}
				catch (Exception e) {
					log.warning(String.format("Exception checking %s (%s)", ip, e));
				}
				
				response.addHeader("Access-Control-Allow-Origin", "*");
				response.setJS(js);
			}
		});
	}

	private static String getClientIP(Config cfg, Request request) {

		// the "real" one
		
		String ip = request.RemoteAddress;

		if (!cfg.AllowOverride) {
			return(ip);
		}

		// look in query string
		
		String qsIP = request.QueryParams.get(cfg.OverrideMarker);
		if (qsIP != null && !qsIP.isEmpty()) {
			
			log.fine(String.format("Overriding ip %s with %s in QS", ip, qsIP));
			return(qsIP);
		}

		// and referrer
		
		String referrer = request.Referrer;
		if (referrer != null) {
			int ichStart = referrer.indexOf(cfg.OverrideMarker + "=");
			if (ichStart != -1) {
				ichStart += cfg.OverrideMarker.length() + 1;
				int ichMac = referrer.indexOf("&", ichStart);
				if (ichMac == -1) ichMac = referrer.length();

				String refIP = referrer.substring(ichStart, ichMac);
						
				log.fine(String.format("Overriding ip %s with %s in REF", ip, refIP));
				return(refIP);
			}
		}

		// nope

		return(ip);
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
