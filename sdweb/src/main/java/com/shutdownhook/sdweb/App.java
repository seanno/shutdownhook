/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.sdweb;

import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;

public class App 
{
	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			System.err.println("java ... [path to config]");
			return;
		}

		Easy.setSimpleLogFormat("INFO");

		String json = Easy.stringFromSmartyPath(args[0]);
		WebServer.Config cfg = new Gson().fromJson(json, WebServer.Config.class);
		
		WebServer server = WebServer.create(cfg);

		if (Easy.nullOrEmpty(cfg.StaticPagesDirectory)) {
			log.info("No static pages configured; adding /echo");
			registerEchoHandler(server);
		}

		try { server.runSync(); }
		finally { server.close(); }
	}

	private static void registerEchoHandler(WebServer server) {

		server.registerHandler("/echo", new WebServer.Handler() {
				
			public void handle(WebServer.Request request, WebServer.Response response) throws Exception {

				String echo = request.QueryParams.get("msg");
				if (Easy.nullOrEmpty(echo)) echo = "No msg query param found";

				if (request.User != null) {
					echo = String.format("%s, %s (%s)", echo,
										 request.User.Id,
										 request.User.Email);
				}
				
				response.setText(echo);
			}
		});
		
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
