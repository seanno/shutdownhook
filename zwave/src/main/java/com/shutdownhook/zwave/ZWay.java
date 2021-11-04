/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// ZWay documentation at https://z-wave.me/z-way/

package com.shutdownhook.zwave;

import java.io.Closeable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class ZWay implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public String Login;
		public String Password;

		public String BaseUrl = REMOTE_BASE_URL;

		public WebRequests.Config Requests = new WebRequests.Config();

		public Boolean UpdateOnCommand = true;
		public Integer MaxUpdateRefreshIterations = 3;
		public Integer UpdateRefreshIntervalMilliseconds = 750;
		public Boolean AllowReferenceByName = true;
		public Boolean DebugPrintFetchBody = false;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public ZWay(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);

		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.parser = new JsonParser();
	}
	
	public void close() {
		logout();
		requests.close();
	}

	// +----------+
	// | Commands |
	// +----------+

	public void turnOn(String deviceId) throws Exception {
		fetch(String.format("devices/%s/command/on", lookupDeviceId(deviceId)));
		if (cfg.UpdateOnCommand) requestUpdate(deviceId);
	}

	public void turnOff(String deviceId) throws Exception {
		fetch(String.format("devices/%s/command/off", lookupDeviceId(deviceId)));
		if (cfg.UpdateOnCommand) requestUpdate(deviceId);
	}

	public void setLevel(String deviceId, int level) throws Exception {
		fetch(String.format("devices/%s/command/exact?level=%d",
							lookupDeviceId(deviceId), level));

		if (cfg.UpdateOnCommand) requestUpdate(deviceId);
	}

	// +--------+
	// | Status |
	// +--------+

	public void requestUpdate(String deviceId) throws Exception {
		
		if (deviceId == null) {
			for (Device device : getDevices().values()) {
				fetch(String.format("devices/%s/command/update", device.getId()));
			}
		}
		else {
			fetch(String.format("devices/%s/command/update", lookupDeviceId(deviceId)));
		}
	}
	
	public int getLevel(String deviceId, boolean refresh) throws Exception {
		JsonObject json = getMetrics(deviceId, refresh);
		return(json.get("level").getAsInt());
	}

	public JsonObject getMetrics(String deviceId, boolean refresh) throws Exception {
		String realDeviceId = lookupDeviceId(deviceId);

		JsonObject json = fetch(String.format("devices/%s", realDeviceId));
		long updated = json.getAsJsonObject("data").get("updateTime").getAsLong();

		if (refresh) {

			requestUpdate(deviceId);

			long newUpdated = updated;
			int iter = 0;
		
			while (newUpdated == updated && iter++ < cfg.MaxUpdateRefreshIterations) {
				Thread.sleep(cfg.UpdateRefreshIntervalMilliseconds);
				json = fetch(String.format("devices/%s", realDeviceId));
				newUpdated = json.getAsJsonObject("data").get("updateTime").getAsLong();
			}

			if (newUpdated == updated) {
				String msg = String.format("No update to %s after %d iterations; " +
										   "value may be stale",
										   deviceId, cfg.MaxUpdateRefreshIterations);
				log.warning(msg);
			}
		}
		
		return(json.getAsJsonObject("data").getAsJsonObject("metrics"));
	}

	// +-------------+
	// | Device List |
	// +-------------+

	public static class Device
	{
		public Device(JsonObject json) {
			this.json = json;
		}

		public String getId() {
			return(json.get("id").getAsString());
		}
		
		public String getType() {
			return(json.get("deviceType").getAsString());
		}

		public String getName() {
			return(json.getAsJsonObject("metrics").get("title").getAsString());
		}
		
		public JsonObject getJson() {
			return(json);
		}
		
		private JsonObject json;
	}

	public Map<String,Device> getDevices() throws Exception {
		return(getDevices(false));
	}
	
	public synchronized Map<String,Device> getDevices(boolean refresh) throws Exception {

		if (devices == null || refresh) {
			
			JsonObject json = fetch("devices");
		
			JsonArray jsonDevices =
				json.getAsJsonObject("data").getAsJsonArray("devices");

			devices = new HashMap<String,Device>();
			for (int i = 0; i < jsonDevices.size(); ++i) {
				Device device = new Device(jsonDevices.get(i).getAsJsonObject());
				devices.put(device.getId(),device);
			}
		}

		return(devices);
	}

	// +----------------+
	// | Authentication |
	// +----------------+

	private void ensureLogin() throws Exception {

		if (zwaySession != null && Instant.now().isAfter(expires)) {
			expires = Instant.MAX; // don't loop forever!
			logout();
		}

		if (zwaySession != null) return;

		if (cfg.BaseUrl.equals(REMOTE_BASE_URL)) remoteLogin();
		else directLogin();

		StringBuilder sb = new StringBuilder();
		sb.append(ZWAY_SESSION_HEADER).append("=").append(zwaySession).append("; ");
		
		if (zbwSessId != null) {
			sb.append(ZBW_SESSID_HEADER).append("=").append(zbwSessId).append(";");
		}
		
		authCookie = sb.toString();
		expires = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS);
	}

	private void remoteLogin() throws Exception {

		String url = Easy.urlPaste(cfg.BaseUrl, "zboxweb");

		Map<String,String> form = new HashMap<String,String>();
		form.put("act", "auth");
		form.put("login", cfg.Login);
		form.put("pass", cfg.Password);

		WebRequests.Params params = new WebRequests.Params();
		params.setForm(form);

		WebRequests.Response response = requests.fetch(url, params);
		if (!response.successful()) response.throwException(url);

		for (String val : response.Headers.get("Set-Cookie")) {
			if (val.startsWith(ZWAY_SESSION_HEADER)) {
				int ichStart = ZWAY_SESSION_HEADER.length() + 1;
				int ichEnd = val.indexOf(";", ichStart);
				zwaySession = val.substring(ichStart, ichEnd);
			}
			else if (val.startsWith(ZBW_SESSID_HEADER)) {
				int ichStart = ZBW_SESSID_HEADER.length() + 1;
				int ichEnd = val.indexOf(";", ichStart);
				zbwSessId = val.substring(ichStart, ichEnd);
			}
		}

		if (zwaySession == null || zbwSessId == null) {
			throw new Exception("Successful remote login but missing zwaySession/zbwSessId");
		}
	}

	private void directLogin() throws Exception {
		
		JsonObject json = new JsonObject();
		json.addProperty("login", cfg.Login);
		json.addProperty("password", cfg.Password);

		JsonObject jsonResponse = fetch("login", json, false);

		zwaySession = jsonResponse.getAsJsonObject("data").get("sid").getAsString();
		zbwSessId = null;
	}

	private void logout() {

		if (zwaySession != null) {
			try {
				fetch("logout");
			}
			catch (Exception e) {
				log.warning("Exception logging out; swallowing");
			}
		}

		zwaySession = null;
		zbwSessId = null;
		authCookie = null;
		expires = null;
	}
	
	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private String lookupDeviceId(String input) throws Exception {

		// skip all the stuff below if we are requiring IDs
		if (!cfg.AllowReferenceByName) return(input);
		
		Map<String,Device> devices = getDevices(false);
		if (devices.containsKey(input)) return(input);

		for (Device device : devices.values()) {
			if (input.equalsIgnoreCase(device.getName())) {
				return(device.getId());
			}
		}

		throw new Exception("Unknown device id or name: " + input);
	}
	
	private JsonObject fetch(String url) throws Exception {
		return(fetch(url, null, true));
	}

	private JsonObject fetch(String url, JsonObject json) throws Exception {
		return(fetch(url, json, true));
	}

	private JsonObject fetch(String url, JsonObject json, boolean auth) throws Exception {

		String fullUrl =
			Easy.urlPaste(Easy.urlPaste(cfg.BaseUrl, API_PATH_PREFIX), url);

		WebRequests.Params params = new WebRequests.Params();

		if (json != null) {
			params.setContentType("application/json");
			params.Body = gson.toJson(json);
		}

		if (auth) {
			ensureLogin();
			params.addHeader("Cookie", authCookie);
		}

		WebRequests.Response response = requests.fetch(fullUrl, params);
		if (!response.successful()) response.throwException(fullUrl);

		if (cfg.DebugPrintFetchBody) {
			System.out.println(response.Body);
		}
		
		JsonObject jsonResponse = parser.parse(response.Body).getAsJsonObject();
		if (jsonResponse.has("code")) {

			response.Status = jsonResponse.get("code").getAsInt();
			if (!response.successful()) {
				
				StringBuilder sb = new StringBuilder();
				if (jsonResponse.has("message")) {
					sb.append(jsonResponse.get("message"));
				}
				if (jsonResponse.has("error")) {
					if (sb.length() > 0) sb.append("; ");
					sb.append(jsonResponse.get("error"));
				}

				response.StatusText = sb.toString();
				response.throwException(fullUrl);
			}
		}

		return(jsonResponse);
	}

	private String zwaySession;
	private String zbwSessId;
	private String authCookie;
	private Instant expires;

	private Map<String,Device> devices;
	
	private Config cfg;
	private WebRequests requests;

	private Gson gson;
	private JsonParser parser;
	
	private static String REMOTE_BASE_URL = "https://find.z-wave.me";
	private static String API_PATH_PREFIX = "/ZAutomation/api/v1";
	
	private static String ZWAY_SESSION_HEADER = "ZWAYSession";
	private static String ZBW_SESSID_HEADER = "ZBW_SESSID";

	private static long TOKEN_LIFETIME_SECONDS = (6L * 24L * 60L * 60L);
	
	private final static Logger log = Logger.getLogger(ZWay.class.getName());
}
