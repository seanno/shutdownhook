/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.smart;

import java.io.Closeable;
import java.util.*;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

import com.google.gson.Gson;

public abstract class SmartServer implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+
	
	public static class Config
	{
		public SmartEhr.Config Smart;
		public WebServer.Config Server;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public SmartServer(Config cfg) throws Exception {

		this.cfg = cfg;
		this.smart = new SmartEhr(cfg.Smart);

		this.server = WebServer.create(cfg.Server);
		
		registerLaunchHandler();
		registerReturnHandler();
		registerAdditionalHandlers(server, smart);
	}

	public void start() {
		server.start();
	}

	public void close() {
		if (server != null) { server.close(); server = null; }
		if (smart != null) { smart.close(); smart = null; }
	}
	
	public void runSync() {
		try {
			server.runSync();
		}
		finally {
			// server is already closed by server.runSync
			server = null;
			close();
		}
	}

	// +---------------+
	// | Implement me! |
	// +---------------+

	abstract public void home(Request request, Response response,
							  SmartEhr.Session session, SmartEhr smart) throws Exception;

	protected void registerAdditionalHandlers(WebServer server,
											  SmartEhr smart) throws Exception { }

	// +----------------+
	// | SessionHandler |
	// +----------------+

	private final static String COOKIE_NAME = "SmartSession";

	public abstract static class SessionHandler implements Handler
	{
		public SessionHandler(SmartEhr smart) {
			this.smart = smart;
		}
		
		public void handle(Request request, Response response) throws Exception {
			
			String sessionCookie = request.Cookies.get(COOKIE_NAME);
			SmartEhr.Session session = smart.rehydrate(sessionCookie);

			handle2(request, response, session);

			String val = smart.dehydrateIfUpdated(session);
			if (val != null) response.setSessionCookie(COOKIE_NAME, val, request);
		}

		protected abstract void handle2(Request request, Response response,
										SmartEhr.Session session) throws Exception;

		private SmartEhr smart;
	}

	// +----------+
	// | Handlers |
	// +----------+

	private final static String LAUNCH_PATH = "/launch";
	private final static String RETURN_PATH = "/return";
	
	private void registerLaunchHandler() {
		server.registerHandler(LAUNCH_PATH, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String siteId = request.QueryParams.get("siteid");
				String launch = request.QueryParams.get("launch");
				String iss = request.QueryParams.get("iss");

				String returnUrl = request.Base + RETURN_PATH;
				String redirectUrl = smart.launch(siteId, launch, iss, returnUrl);
				log.info("Redirecting to " + redirectUrl);

				response.redirect(redirectUrl);
			}
		});
	}

	private void registerReturnHandler() {
		server.registerHandler(RETURN_PATH, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String code = request.QueryParams.get("code");
				String state = request.QueryParams.get("state");

				String returnUrl = request.Base + RETURN_PATH;
				SmartEhr.Session session = smart.successfulAuth(code, state, returnUrl);

				response.setSessionCookie(COOKIE_NAME, smart.dehydrate(session), request);
				home(request, response, session, smart);
			}
		});
	}
	
	// +--------------------+
	// | Fields and Helpers |
	// +--------------------+

	private Config cfg;
	private SmartEhr smart;
	private WebServer server;
	
	private final static Logger log = Logger.getLogger(SmartServer.class.getName());
}

