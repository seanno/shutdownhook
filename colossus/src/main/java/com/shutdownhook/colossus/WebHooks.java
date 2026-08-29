//
// WEBHOOKS.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

public class WebHooks implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public Map<String,String> LocationPaths = new HashMap<String,String>();
		public String LocationBasicUser;
		public String LocationBasicPass_S;

		public String[] ProxyHosts;
		public String ProxyContentAgent = "Claude-User";
			
		public WebServer.Config WebServer = new WebServer.Config();
		public String LoggingConfigPath = "@logging-webhooks.properties";

		public String LocationUpdateUrl = "/loc";
		public String LocationUpdateTag = "who";

		public String ProxyUrl = "/pxy";
		public String ProxyUrlParam = "s";

		public WebRequests.Config WebRequests = new WebRequests.Config();
		
		public static Config fromJson(String json) {
			Config cfg = new Gson().fromJson(json, Config.class);
			if (cfg.LocationPaths.size() == 0) cfg.LocationPaths.put("*", "/tmp/location.json");
			return(cfg);
		}
	}

	public WebHooks(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.WebRequests);
		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {
		server = WebServer.create(cfg.WebServer);
		registerLocationUpdate();
		registerProxy();
	}

	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); requests.close(); }

	// +---------------+
	// | registerProxy |
	// +---------------+

	// for requests with user agents containing cfg.ProxyContentAgent, fetch and
	// return the content directly. Otherwise issue a redirect to the original url.
	// Limited to hosts in cfg.ProxyHosts --- others will result in an error response.
	
	private void registerProxy() throws Exception {

		server.registerHandler(cfg.ProxyUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				// only accept GET
				if (!"GET".equals(request.Method)) {
					response.Status = 404; return;
				}

				// check for legit host and url values
				String url = request.QueryParams.get(cfg.ProxyUrlParam);
				if (url == null || cfg.ProxyHosts == null || cfg.ProxyHosts.length == 0) {
					response.Status = 500; return;
				}

				String host = new URL(url).getHost();
				boolean found = false;
				for (String proxyHost : cfg.ProxyHosts) {
					if (proxyHost.equalsIgnoreCase(host)) { found = true; break; }
				}

				if (!found) { response.Status = 500; return; }

				// check the user agent --- just redirect unless it's the content agent
				String ua = request.getHeader("User-Agent");
				if (ua == null || ua.indexOf(cfg.ProxyContentAgent) == -1) {
					response.redirect(url);
					return;
				}

				// fetch and return as text; this is really brain dead and will
				// only work for very simple browser fetches. 
				WebRequests.Response webResponse = requests.fetch(url);
				response.Body = webResponse.Body;
				response.ContentType = webResponse.getFirstHeader("Content-Type");

				if (response.ContentType.startsWith("text/html")) {
					response.setText(extractTextFromHtml(response.Body));
				}
				
				response.Status = 200;
			}
		});
		
	}

	private String extractTextFromHtml(String body) {
		try {
			Document doc = Jsoup.parse(body);
			String cleanText = doc.body().text();
			return(cleanText);
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "jsoup", true));
			return(body);
		}
	}

	// +------------------------+
	// | registerLocationUpdate |
	// +------------------------+

	private void registerLocationUpdate() throws Exception {

		server.registerHandler(cfg.LocationUpdateUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				// only accept POST
				if (!"POST".equals(request.Method)) {
					response.Status = 404;
					return;
				}

				// require BASIC auth, maybe
				if (cfg.LocationBasicUser != null && cfg.LocationBasicPass_S != null) {
					String pass = Easy.smartyGetProperty(cfg.LocationBasicPass_S);
					if (!checkAuth(request, cfg.LocationBasicUser, pass)) {
						response.Status = 401;
						return;
					}
				}

				// make sure body is parsable json (will except if not)
				JsonObject bodyJson = JsonParser.parseString(request.Body).getAsJsonObject();
				if (bodyJson.get("lat") != null && bodyJson.get("lon") != null) {
					// save the file
					String path = findLocationPath(request);
					if (path == null) log.warning("no matching location path: " + request.QueryString);
					else Easy.stringToFile(path, request.Body);
				}
				
				// owntracks requires a json array response
				response.setJson("[]");
			}
		});
		
	}

	private String findLocationPath(Request request) {
		
		String queryWho = request.QueryParams.get(cfg.LocationUpdateTag);
		if (queryWho == null || !cfg.LocationPaths.containsKey(queryWho)) queryWho = "*";
		
		String path = cfg.LocationPaths.get(queryWho);
		if (path == null) log.warning("Warning: got location request for invalid who");

		return(path);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private boolean checkAuth(Request request, String user, String pass) {
		
		List<String> authHeaders = request.Headers.get("Authorization");
		if (authHeaders == null || authHeaders.size() == 0) return(false);
		
		String authHeader = authHeaders.get(0);
		if (!authHeader.startsWith("Basic ")) return(false);

		String authBase64 = authHeader.substring(6).trim(); // skip the basic part
		String authInfo = Easy.base64Decode(authBase64);
			
		int ichUser = authInfo.indexOf(":");
		if (ichUser == -1) return(false);
		
		String authUser = authInfo.substring(0, ichUser);
		String authPass = authInfo.substring(ichUser + 1);

		return(user.equals(authUser) && pass.equals(authPass));
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {
		
		if (args.length < 1) {
			System.out.println("Usage java -cp [COLOSSUS.JAR] com.shutdownhook.colossus.WebHooks [CFG]");
			return;
		}

		Config cfg = Config.fromJson(Easy.stringFromFile(args[0]));
		Easy.configureLoggingProperties(cfg.LoggingConfigPath);
		WebHooks hooks = new WebHooks(cfg);

		try { hooks.runSync(); }
		finally { hooks.close(); }
	}
	
	// +---------+
	// | Members |
	// +---------+

	Config cfg;
	WebServer server;
	WebRequests requests;
	
	private final static Logger log = Logger.getLogger(WebHooks.class.getName());
}
