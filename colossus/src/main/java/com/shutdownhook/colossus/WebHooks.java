//
// WEBHOOKS.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
		public String LocationReverseGeocoderURLFmt;
		public boolean ReverseGeocoderIsXML;
			
		public WebServer.Config WebServer = new WebServer.Config();
		public String LoggingConfigPath = "@logging-webhooks.properties";

		public String LocationUpdateUrl = "/loc";
		public String LocationUpdateTag = "who";

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
	}

	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); requests.close(); }

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
				
				// verify the save path
				String path = findLocationPath(request);
				if (path == null) {
					response.Status = 500;
					return;
				}

				// possibly reverse geocode
				String output = maybeReverseGeocode(bodyJson);

				// save and out
				// owntracks requires a json array response
				Easy.stringToFile(path, output);
				response.setJson("[]");
			}
		});
		
	}

	private String maybeReverseGeocode(JsonObject ownTracksJson) {

		if (cfg.LocationReverseGeocoderURLFmt == null) return(ownTracksJson.toString());

		double lat = ownTracksJson.get("lat").getAsDouble();
		double lng = ownTracksJson.get("lon").getAsDouble(); // careful of these labels!
		
		String url = String.format(cfg.LocationReverseGeocoderURLFmt, lat, lng);
		WebRequests.Response webResponse = requests.fetch(url);
		if (!webResponse.successful()) {
			log.warning(String.format("Failed reverse geocode %d: %s (%s)",
									  webResponse.Status, webResponse.StatusText,
									  webResponse.Ex));
			
			return(ownTracksJson.toString());
		}

		// check for stupid xml
		String output = webResponse.Body;
		if (cfg.ReverseGeocoderIsXML) output = org.json.XML.toJSONObject(output).toString();

		return(output);
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
