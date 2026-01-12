//
// SMARTHQRESOURCE.JAVA
//
// Params: tsv (url to spreadsheet; see ServiceCheckInfo section for format)
//         username
//         password
//         clientId
//         clientSecret
//         redirectUri (optional; defaults to DEFAULT_REDIRECT_URI)
//         debug (optional: true sends full JSON for each device)
//
// https://docs.smarthq.com/
//
// * 1 ERROR status for each OFFLINE device
// * 1 OK status for all ONLINE devices with no warnings or errors
// * 1 WARNING status for each ONLINE device with warnings (result = list of warnings)
// * 1 ERROR status for each ONLINE device with errors (result = list of errors)
//

package com.shutdownhook.backstop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class SmartHqResource implements Checker
{
	private static String DEFAULT_REDIRECT_URI =
		"https://localhost/oauth/redirect";
	
	private static String AUTH_URL_PREFIX =
		"https://accounts.brillion.geappliances.com/oauth2/auth?response_type=code&client_id=";
	
	private static String LOGIN_POST_URL =
		"https://accounts.brillion.geappliances.com/oauth2/g_authenticate";

	private static String AUTHORIZE_POST_URL =
		"https://accounts.brillion.geappliances.com/oauth2/code";

	private static String TOKEN_POST_URL =
		"https://accounts.brillion.geappliances.com/oauth2/token";
	
	private static String API_BASE_URL =
		"https://client.mysmarthq.com";

	// +-------+
	// | check |
	// +-------+

	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  String stateId,
					  List<Status> statuses) throws Exception {

		this.user = params.get("user");
		this.password = params.get("password");
		this.clientId = params.get("clientId");
		this.clientSecret = params.get("clientSecret");
		this.helpers = helpers;
		this.statuses = statuses;

		String tsv = params.get("tsv");
		this.checkInfos = loadCheckInfos(tsv);

		String debugStr = params.get("debug");
		this.debug = ("true".equalsIgnoreCase(debugStr) ? true : false);
		
		this.redirectUri = params.get("redirectUri");
		if (Easy.nullOrEmpty(this.redirectUri)) this.redirectUri = DEFAULT_REDIRECT_URI;

		this.browser = new SessionBrowser(helpers.getRequests());

		String token = getToken();
		Device[] devices = getDevices(token);

		StringBuilder sbOKDevices = new StringBuilder();
		
		for (Device device : devices) {
			
			String name = getName(device);

			if (!"ONLINE".equals(device.presence)) {
				statuses.add(new Status(name, StatusLevel.ERROR, "Device offline"));
				continue;
			}
			
			try { 
				if (checkDeviceServices(name, device.deviceId, token)) {
					if (sbOKDevices.length() > 0) sbOKDevices.append(", ");
					sbOKDevices.append(name);
				}
			}
			catch (Exception e) {
				statuses.add(new Status(name, StatusLevel.ERROR, e.getMessage()));
			}
		}

		if (sbOKDevices.length() > 0) {
			statuses.add(new Status("", StatusLevel.OK, sbOKDevices.toString()));
		}
	}

	// +---------------------+
	// | checkDeviceServices |
	// +---------------------+

	private boolean checkDeviceServices(String name, String deviceId, String token) throws Exception {

		String relativeUrl = String.format("/v2/device/%s", deviceId);
		String json = apiRequest(relativeUrl, "checkDevice", token);
		Device device = helpers.getGson().fromJson(json, Device.class);

		if (debug) System.out.println("===== " + name + "\n" + json + "\n=====");
		
		StringBuilder sbWarn = new StringBuilder();
		StringBuilder sbErr = new StringBuilder();
		
		if (device.services == null || device.services.length == 0) return(true);

		LocalDate today = helpers.getDateToday();
		
		// inefficient N^2 searching but these are small so fine
		for (ServiceCheckInfo checkInfo : checkInfos) {
			for (Service service : device.services) {

				String msg = checkInfo.fault(service, today);
				if (Easy.nullOrEmpty(msg)) continue;
				
				if (checkInfo.Level.equals(StatusLevel.WARNING)) {
					if (sbWarn.length() > 0) sbWarn.append(", ");
					sbWarn.append(msg);
				}
				else {
					if (sbErr.length() > 0) sbErr.append(", ");
					sbErr.append(msg);
				}
			}
		}

		if (sbWarn.length() > 0) {
			statuses.add(new Status(name, StatusLevel.WARNING, sbWarn.toString()));
		}

		if (sbErr.length() > 0) {
			statuses.add(new Status(name, StatusLevel.ERROR, sbErr.toString()));
		}

		return(sbWarn.length() == 0 && sbErr.length() == 0);
	}
	
	// +-------------+
	// | listDevices |
	// +-------------+

	private Device[] getDevices(String token) throws Exception {

		String json = apiRequest("/v2/device", "getDevices", token);
		Devices devices = helpers.getGson().fromJson(json, Devices.class);
		
		if (devices.total > devices.perpage) throw new Exception("may be missing devices");
		
		return(devices.devices);
	}

	private String apiRequest(String relativeUrl, String tag,
							  String token) throws Exception {

		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("Authorization", "Bearer " + token);
			
		String url = Easy.urlPaste(API_BASE_URL, relativeUrl);
		WebRequests.Response response = helpers.getRequests().fetch(url, params);
		
		if (!response.successful()) response.throwException(tag);
		return(response.Body);
	}
	
	// +----------+
	// | getToken |
	// +----------+

	// Mimic a user in a browser to log in and get an oauth token

	private String getToken() throws Exception {

		// 1. Fetch the human login page and extract form elements
		
		String url = AUTH_URL_PREFIX + clientId;
		WebRequests.Response response = browser.fetch(url, "gt1");
		if (!response.successful()) response.throwException("gt1");

		Map<String,String> postForm = getFormElements(response.Body);
		postForm.put("username", user);
		postForm.put("password", password);

		// 2a. Post to the authentication endpoint
		
		WebRequests.Params params = browser.getParams();
		params.setForm(postForm);
		params.FollowRedirects = false;
		response = browser.fetch(LOGIN_POST_URL, params, "gt2a");

		if (response.Status == 200) {

			// 2b. User hasn't authorized the app yet, so do that
			postForm = getFormElements(response.Body);
			postForm.put("authorized", "yes");

			params = browser.getParams();
			params.setForm(postForm);

			params.FollowRedirects = false;
			response = browser.fetch(AUTHORIZE_POST_URL, params, "gt2b");
		}

		// 3. Extract the code

		// ok for this to be not super-defensive; exceptions or missing codes
		// are failure modes and exceptions here or in the next step will deal
		String location = response.Headers.get("Location").get(0);
		int ichCodeStart = location.indexOf("code=") + 5;
		int ichCodeEnd = location.indexOf("&", ichCodeStart);
		if (ichCodeEnd == -1) ichCodeEnd = location.length();
		String code = location.substring(ichCodeStart, ichCodeEnd);

		// 4. Swap for a token
		
		postForm = new HashMap<String,String>();
		postForm.put("grant_type", "authorization_code");
		postForm.put("code", code);
		postForm.put("redirect_uri", redirectUri);
		postForm.put("client_id", clientId);
		postForm.put("client_secret", clientSecret);
		
		params = browser.getParams();
		params.setForm(postForm);
		params.FollowRedirects = false;

		response = browser.fetch(TOKEN_POST_URL, params, "gt4");
		if (!response.successful()) response.throwException("gt4");

		// 5. and return it
		
		return(helpers.getGson()
			   .fromJson(response.Body, TokenResponse.class)
			   .access_token);
	}

	private Map<String,String> getFormElements(String html) throws Exception {

		Document authDoc = Jsoup.parse(html);
		Element formElt = authDoc.getElementById("frmsignin");

		Map<String,String> postForm = new HashMap<String,String>();
		
		for (Element inputElt : formElt.getElementsByTag("input")) {
			Attribute nameAttr = inputElt.attribute("name");
			String val = inputElt.val();
			if (nameAttr == null || Easy.nullOrEmpty(val)) continue;
			postForm.put(nameAttr.getValue(), val);
		}

		return(postForm);
	}

	// +------------------+
	// | ServiceCheckInfo |
	// +------------------+

	public static enum CompareOperator
	{
		EQ,
		NEQ,
		GT,
		LT
	}
	
	public static class ServiceCheckInfo
	{
		public LocalDate SnoozeUntil;
		public String Message;
		public StatusLevel Level;
		public String Service;
		public String Domain;
		public String Device;
		public String StateKey;
		public CompareOperator CheckComparison;
		public String CheckValue;

		public static ServiceCheckInfo fromTsvRow(String line, int irow, BackstopHelpers helpers) throws Exception {

			if (line.isEmpty() || line.startsWith("#")) return(null);

			String[] fields = line.split("\t");
			if (fields.length < 9) throw new Exception(String.format("Spreadsheet malformed row %d, %d fields (%s)",
																	 irow + 1, fields.length, line));

			ServiceCheckInfo sci = new ServiceCheckInfo();

			String snoozeStr = fields[0].trim();
			if (!Easy.nullOrEmpty(snoozeStr)) {
				sci.SnoozeUntil = helpers.parseUSDate(snoozeStr);
			}

			sci.Message = fields[1].trim();
			sci.Level = StatusLevel.valueOf(fields[2].trim());
			
			sci.Service = fields[3].trim(); 
			sci.Domain = fields[4].trim(); 
			sci.Device = fields[5].trim();
			
			sci.StateKey = fields[6].trim(); 
			sci.CheckComparison = CompareOperator.valueOf(fields[7].trim());
			sci.CheckValue = fields[8].trim();

			return(sci);
		}

		public String fault(Service service, LocalDate today) throws Exception {

			if (SnoozeUntil != null && today.isBefore(SnoozeUntil)) {
				return(null); // snoozed
			}
			
			if ((!Easy.nullOrEmpty(Service) && !Service.equals(service.serviceType)) ||
				(!Easy.nullOrEmpty(Domain) && !Domain.equals(service.domainType)) ||
				(!Easy.nullOrEmpty(Device) && !Device.equals(service.serviceDeviceType))) {
				return(null); // no match
			}

			String stateValue = service.state.get(StateKey);

			boolean ok;
			  
			switch (CheckComparison) {
				case EQ: ok = CheckValue.equals(stateValue); break;
				case NEQ: ok = !CheckValue.equals(stateValue); break;
				case GT: ok = (Integer.parseInt(stateValue) > Integer.parseInt(CheckValue)); break;
				case LT: ok = (Integer.parseInt(stateValue) < Integer.parseInt(CheckValue)); break;
				default: throw new Exception("wtf");
			}

			return(ok ? null : Message.replace("{{STATE}}", stateValue));
		}
	}

	private List<ServiceCheckInfo> loadCheckInfos(String tsv) throws Exception {
		
		WebRequests.Response response = helpers.getRequests().fetch(tsv);
		if (!response.successful()) {
			throw new Exception(String.format("%s failed: (%d) %s", tsv,
											  response.Status, response.StatusText));
		}

		List<ServiceCheckInfo> checkInfos = new ArrayList<ServiceCheckInfo>();
		
		String[] lines = response.Body.split("\n");
		
		for (int i = 1; i < lines.length; ++i) { // note skips header row
			ServiceCheckInfo sci = ServiceCheckInfo.fromTsvRow(lines[i], i, helpers);
			if (sci != null) checkInfos.add(sci);
		}

		return(checkInfos);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String getName(Device device) {
		if (!Easy.nullOrEmpty(device.nickname)) return(device.nickname);
		return(lastSectionOf(device.deviceType));
	}

	private String lastSectionOf(String dottedInput) {
		if (Easy.nullOrEmpty(dottedInput)) return("");
		int ich = dottedInput.lastIndexOf(".");
		return(dottedInput.substring(ich == -1 ? 0: ich, dottedInput.length()));
	}

	// +----------------------------+
	// | Types for JSON marshalling |
	// +----------------------------+

	public static class Devices
	{
		public int perpage;
		public int total;
		public Device[] devices;
	}
	
	public static class Device
	{
		public String deviceType;
		public String lastSyncTime;
		public String serial;
		public String lastPresenceTime;
		public String presence;
		public String deviceId;
		public String nickname;
		public String model;
		public String manufacturer;
		public Service[] services;
	}
	
	public static class Service
	{
		public String serviceType;
		public String domainType;
		public String serviceDeviceType;
		public Map<String,String> state;
	}
	
	public static class TokenResponse
	{
		public String access_token;
		public String refresh_token;
		public String token_type;
		public int expires_in;
	}

	// +---------+
	// | Members |
	// +---------+

	private String user;
	private String password;
	private String clientId;
	private String clientSecret;
	private String redirectUri;
	private boolean debug;

	private BackstopHelpers helpers;
	private List<Status> statuses;
	private SessionBrowser browser;

	private List<ServiceCheckInfo> checkInfos;
}
