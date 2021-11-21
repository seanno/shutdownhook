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
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.Worker;

public class ZWay extends Worker implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public String Login;
		public String Password;

		public String BaseUrl = REMOTE_BASE_URL;

		public List<CommandDeviceConfig> CommandDevices;
		
		public WebRequests.Config Requests = new WebRequests.Config();

		public Boolean UpdateOnCommand = true;
		public Integer MaxUpdateRefreshIterations = 3;
		public Integer UpdateRefreshIntervalMilliseconds = 750;
		public Boolean AllowReferenceByName = true;
		public Boolean DebugPrintFetchBody = false;

		public Integer UpdateCatchupIntervalSeconds = (60 * 15); // 0 = no catchup thread
		public Integer StopWaitSeconds = 10;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public static class CommandDeviceConfig
	{
		public String Id;
		public String Name;
		public String Type;

		public String CommandOn;
		public String CommandOff;
		public String CommandGet;
		public String CommandSetFormat;
	}

	public ZWay(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);

		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.parser = new JsonParser();

		if (cfg.UpdateCatchupIntervalSeconds == 0) {
			log.info("No update catchup thread configured");
		}
		else {
			log.info("Starting update catchup thread...");
			go();
		}
	}
	
	public void close() {

		if (cfg.UpdateCatchupIntervalSeconds > 0) {
			log.info("Stopping update catchup thread...");
			signalStop();
			waitForStop(cfg.StopWaitSeconds);
		}

		logout();
		requests.close();
	}

	// +--------+
	// | Status |
	// +--------+

	public void updateAll() throws Exception {
		for (Device device : getDevices().values()) device.update();
	}
	
	// +--------+
	// | Device |
	// +--------+

	abstract static class Device
	{
		public Device(ZWay zway) {
			this.zway = zway;
		}
		
		abstract public String getId();
		abstract public String getName();
		abstract public String getType();
		
		abstract public void turnOn() throws Exception;
		abstract public void turnOff() throws Exception;
		abstract public int getLevel(boolean refresh) throws Exception;
		abstract public void setLevel(int level) throws Exception;
		abstract public void update() throws Exception;

		protected ZWay zway;
	}

	// +------------+
	// | ZWayDevice |
	// +------------+

	public static class ZWayDevice extends Device
	{
		public ZWayDevice(ZWay zway, JsonObject json) {
			super(zway);
			this.json = json;
		}
		
		public String getId() {
			return(json.get("id").getAsString());
		}

		public String getName() {
			return(json.getAsJsonObject("metrics").get("title").getAsString());
		}
		
		public String getType() {
			return(json.get("deviceType").getAsString());
		}

		public void turnOn() throws Exception {
			zway.fetch(String.format("devices/%s/command/on", getId()));
			if (zway.cfg.UpdateOnCommand) update();
		}
		
		public void turnOff() throws Exception {
			zway.fetch(String.format("devices/%s/command/off", getId()));
			if (zway.cfg.UpdateOnCommand) update();
		}
		
		public void setLevel(int level) throws Exception {
			zway.fetch(String.format("devices/%s/command/exact?level=%d",
									 getId(), level));
			
			if (zway.cfg.UpdateOnCommand) update();
		}
		
		public int getLevel(boolean refresh) throws Exception {

			JsonObject json = zway.fetch(String.format("devices/%s", getId()));
			long updated = json.getAsJsonObject("data").get("updateTime").getAsLong();
				
			if (refresh) {

				update();

				long newUpdated = updated;
				int iter = 0;
		
				while (newUpdated == updated &&
					   iter++ < zway.cfg.MaxUpdateRefreshIterations) {
					
					Thread.sleep(zway.cfg.UpdateRefreshIntervalMilliseconds);
					json = zway.fetch(String.format("devices/%s", getId()));
					
					newUpdated =
						json.getAsJsonObject("data").get("updateTime").getAsLong();
				}

				if (newUpdated == updated) {
					String msg = String.format("No update to %s after %d iterations; " +
											   "value may be stale", getId(),
											   zway.cfg.MaxUpdateRefreshIterations);
					ZWay.log.warning(msg);
				}
			}

			int level = json.getAsJsonObject("data")
				.getAsJsonObject("metrics")
				.get("level")
				.getAsInt();

			return(level);
		}

		public void update() throws Exception {

			if (!isUpdatable()) return;
			
			zway.fetch(String.format("devices/%s/command/update", getId()));
		}

		private boolean isUpdatable() {

			if (!json.has("probeType")) return(true);

			String probeType = json.get("probeType").getAsString();
			return(!probeType.equalsIgnoreCase("control"));
		}

		private JsonObject json;
	}

	// +---------------+
	// | CommandDevice |
	// +---------------+

	public static class CommandDevice extends Device
	{
		public CommandDevice(ZWay zway, CommandDeviceConfig cfg) {
			super(zway);
			this.cfg = cfg;
		}
		
		public String getId() { return(cfg.Id); }
		public String getName() { return(cfg.Name); }
		public String getType() { return(cfg.Type); }

		public void turnOn() throws Exception {
			run(cfg.CommandOn);
			cachedLevel = 100;
		}
		
		public void turnOff() throws Exception {
			run(cfg.CommandOff);
			cachedLevel = 0;
		}
		
		public void setLevel(int level) throws Exception {
			if (cfg.CommandSetFormat == null) {
				if (level == 0) turnOff(); else turnOn();
			}
			else {
				run(String.format(cfg.CommandSetFormat, level));
				cachedLevel = level;
			}
		}

		public int getLevel(boolean refresh) throws Exception {

			if (cachedLevel == null || refresh) {
				String result = run(cfg.CommandGet);
				if (result.equalsIgnoreCase("false")) cachedLevel = 0;
				else if (result.equalsIgnoreCase("true")) cachedLevel = 100;
				else cachedLevel = Integer.parseInt(result);
			}

			return(cachedLevel);
		}

		public void update() throws Exception {
			getLevel(true);
		}

		private String run(String cmd) throws Exception {
			String result = Easy.stringFromProcess(cmd);
			ZWay.log.info(String.format("CommandDevice result: %s (%s)", result, cmd));
			return(result.trim());
		}

		private CommandDeviceConfig cfg;
		private Integer cachedLevel = null;
	}

	// +-------------+
	// | Device List |
	// +-------------+

	public Device findDevice(String input) throws Exception {

		Map<String,Device> devices = getDevices(false);
		if (devices.containsKey(input)) return(devices.get(input));

		if (cfg.AllowReferenceByName) {

			for (Device device : devices.values()) {
				if (input.equalsIgnoreCase(device.getName())) {
					return(device);
				}
			}
		}

		throw new Exception("Unknown device id or name: " + input);
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
				Device device = new ZWayDevice(this, jsonDevices.get(i).getAsJsonObject());
				devices.put(device.getId(), device);
			}

			if (cfg.CommandDevices != null) {
				for (CommandDeviceConfig cmdCfg : cfg.CommandDevices) {
					Device device = new CommandDevice(this, cmdCfg);
					devices.put(device.getId(), device);
				}
			}
		}

		return(devices);
	}

	// +-----------------------+
	// | Update Catchup Thread |
	// +-----------------------+

	@Override
	public void work() throws Exception {

		while (!sleepyStop(cfg.UpdateCatchupIntervalSeconds)) {
			try {
				log.info("requesting catchup update");
				updateAll();
			}
			catch (Exception e) {
				log.severe("Exception in zway refresh thread; exiting: " + e.toString());
			}
		}
	}
	
	@Override
	public void cleanup(Exception e) {
		// nut-n-honey
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
