/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Created using documentation at https://tesla-api.timdorr.com/

package com.shutdownhook.tesla;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class Tesla implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	private final static String OWNER_URL =
		"https://owner-api.teslamotors.com";

	public static class Config
	{
		public String Email;
		public String Password;
		public Integer WakeWaitSeconds = 30;
		public Integer MaxWaitRetries = 4;
		public Integer ApiWaitSeconds = 5;
		public Integer MaxApiRetries = 4;
		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public Tesla(Config cfg) throws Exception {
		this.cfg = cfg;
		this.cfg.Requests.FollowRedirects = false;
		requests = new WebRequests(cfg.Requests);
	}

	public void close() {
		requests.close();
	}

	// +----------+
	// | Commands |
	// +----------+

	public boolean honk(String vehicleId) throws Exception {
		ensureAwake(vehicleId);
		String url = "/api/1/vehicles/" + vehicleId + "/command/honk_horn";
		JsonObject json = getOwnerApi(url, true);
		return(json.get("result").getAsBoolean());
	}

	public boolean flash(String vehicleId) throws Exception {
		ensureAwake(vehicleId);
		String url = "/api/1/vehicles/" + vehicleId + "/command/flash_lights";
		JsonObject json = getOwnerApi(url, true);
		return(json.get("result").getAsBoolean());
	}

	// +-----------------+
	// | Specific Values |
	// +-----------------+

	private final static String BING_URL_FMT =
		"https://bing.com/maps/default.aspx?cp=%f~%f&sp=point.%f_%f_%s___&style=a&lvl=18";

	public String getMapUrl(String vehicleId) throws Exception {
		
		JsonObject jsonResponse = getVehicleData(vehicleId).getAsJsonObject("response");
		JsonObject jsonDriveState = jsonResponse.getAsJsonObject("drive_state");

		String name = Easy.urlEncode(jsonResponse.get("display_name").getAsString());
		double latitude = jsonDriveState.get("latitude").getAsDouble();
		double longitude = jsonDriveState.get("longitude").getAsDouble();
		
		return(String.format(BING_URL_FMT, latitude, longitude, latitude, longitude, name));
	}

	public Double getMileage(String vehicleId) throws Exception {

		double odometer = getVehicleData(vehicleId)
			.getAsJsonObject("response")
			.getAsJsonObject("vehicle_state")
			.get("odometer")
			.getAsDouble();

		return(odometer);
	}
	
	public Double getInsideTemp(String vehicleId) throws Exception {

		double c = getVehicleData(vehicleId)
			.getAsJsonObject("response")
			.getAsJsonObject("climate_state")
			.get("inside_temp")
			.getAsDouble();

		return((c * 9.0 / 5.0) + 32.0);
	}

	public Double getOutsideTemp(String vehicleId) throws Exception {

		double c = getVehicleData(vehicleId)
			.getAsJsonObject("response")
			.getAsJsonObject("climate_state")
			.get("outside_temp")
			.getAsDouble();

		return((c * 9.0 / 5.0) + 32.0);
	}

	// +---------------+
	// | Specific JSON |
	// +---------------+

	public JsonObject getVehicles() throws Exception {
		return(getOwnerApi("/api/1/vehicles"));
	}

	public JsonObject getVehicleData(String vehicleId) throws Exception {
		ensureAwake(vehicleId);
		return(getOwnerApi("/api/1/vehicles/" + vehicleId + "/vehicle_data"));
	}

	public JsonObject getNearbyChargers(String vehicleId) throws Exception {
		ensureAwake(vehicleId);
		return(getOwnerApi("/api/1/vehicles/" + vehicleId + "/nearby_charging_sites"));
	}

	// +--------------+
	// | Generic JSON |
	// +--------------+

	public JsonObject getOwnerApi(String relativeUrl) throws Exception {
		return(getOwnerApi(relativeUrl, false));
	}

	public JsonObject getOwnerApi(String relativeUrl, boolean post) throws Exception {

		ensureAuthentication();

		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("Authorization", "Bearer " + accessToken);
		if (post) params.MethodOverride = "POST";

		WebRequests.Response response = null;
		int retries = 0;

		while (retries++ < cfg.MaxApiRetries) {
			
			response = requests.fetch(OWNER_URL + relativeUrl, params);

			if (response.successful()) {
				return(new JsonParser().parse(response.Body).getAsJsonObject());
			}
			else if (response.Status == 408) {
				log.info(String.format("Url %s returned 408; waiting %d seconds (%d)",
									   relativeUrl, cfg.ApiWaitSeconds, retries));

				Thread.sleep(cfg.ApiWaitSeconds * 1000);
			}
			else {
				String msg = String.format("Failed fetching %s (%d/%s)", relativeUrl,
										   response.Status, response.StatusText);
			
				throw new Exception(msg, response.Ex);
			}
		}

		String msg = String.format("Url %s failed; too many retries", relativeUrl);
		log.severe(msg);
		throw new Exception(msg);
	}

	// +----------+
	// | Wokeness |
	// +----------+

	public void ensureAwake(String vehicleId) throws Exception {

		String url = "/api/1/vehicles/" + vehicleId + "/wake_up";
		int retries = 0;
		
		while (retries++ < cfg.MaxWaitRetries) {
			
			JsonObject json = getOwnerApi(url, true);
			String state = json.getAsJsonObject("response").get("state").getAsString();

			if (state.equals("online")) return;

			log.info(String.format("Vehicle %s not online; waiting %d seconds (%d)",
								   vehicleId, cfg.WakeWaitSeconds, retries));

			Thread.sleep(cfg.WakeWaitSeconds * 1000);
		}
	}

	// +----------------+
	// | Authentication |
	// +----------------+

	private final static String SSO_URL =
		"https://auth.tesla.com";

	private final static String CLIENT_ID =
		"81527cff06843c8634fdc09e8ac0abefb46ac849f38fe1e431c2ef2106796384";

	private final static String CLIENT_SECRET =
		"c7257eb71a564034f9419ee651c7d0e5f7aa6bfbd18bafb5c5c033b093bb2fa3";
	
	private final static String HIDDENS_REGEX =
		"<input type=\\\"hidden\\\" name=\\\"([^\\\"]+)\\\" value=\\\"([^\\\"]+)\\\"";

	public static class AuthState
	{
		// added in fetchLoginPage
		public String CodeVerifier;
		public String CodeChallenge;
		public String State;
		public Map<String,String> Hiddens = new HashMap<String,String>();
		public Map<String,String> Cookies = new HashMap<String,String>();
		// added in fetchAuthorizationCode
		public String Code;
		// added in exchangeCodeForToken
		public String AccessTokenInterim;
		// added in exchangeInterimForFinal
		public String AccessToken;
	}
	
	public void ensureAuthentication() throws Exception {
		
		if (accessToken != null) return;

		AuthState state = new AuthState();

		fetchLoginPage(state);
		fetchAuthorizationCode(state);
		exchangeCodeForInterim(state);
		exchangeInterimForFinal(state);

		accessToken = state.AccessToken;
	}

	private void fetchLoginPage(AuthState state) throws Exception {

		state.CodeVerifier = Easy.randomAlphaNumeric(86);
		state.CodeChallenge = Easy.base64Encode(Easy.sha256(state.CodeVerifier));
		state.State = Easy.randomAlphaNumeric(50);

		String url = SSO_URL + "/oauth2/v3/authorize";
		
		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("User-Agent", Tesla.class.getName());
		addAuthQueryStuff(params, state);

		WebRequests.Response response = requests.fetch(url, params);
		if (!response.successful()) throw response.Ex;

		for (String setCookie : response.Headers.get("Set-Cookie")) {
			
			int ichEquals = setCookie.indexOf("=");
			if (ichEquals == -1) throw new Exception("Bad SC: " + setCookie);
			
			int ichSemi = setCookie.indexOf(";", ichEquals);
			if (ichSemi == -1) throw new Exception("Bad SC: " + setCookie);

			state.Cookies.put(setCookie.substring(0, ichEquals),
							  setCookie.substring(ichEquals + 1, ichSemi));
		}

		Pattern pattern = Pattern.compile(HIDDENS_REGEX);
		Matcher matcher = pattern.matcher(response.Body);

		while (matcher.find()) {
			state.Hiddens.put(matcher.group(1), matcher.group(2));
		}
	}

	private void fetchAuthorizationCode(AuthState state) throws Exception {

		String url = SSO_URL + "/oauth2/v3/authorize";
		
		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("User-Agent", Tesla.class.getName());
		addAuthQueryStuff(params, state);

		state.Hiddens.put("identity", cfg.Email);
		state.Hiddens.put("credential", cfg.Password);
		params.setForm(state.Hiddens);

		StringBuilder sb = new StringBuilder();
		for (String name : state.Cookies.keySet()) {
			if (sb.length() > 0) sb.append("; ");
			sb.append(name).append("=").append(state.Cookies.get(name));
		}

		params.addHeader("Cookie", sb.toString());

		WebRequests.Response response = requests.fetch(url, params);
		if (response.Status != 302) throw response.Ex;

		state.Code = findQueryParam(response.Headers.get("Location").get(0), "code");
	}

	private void exchangeCodeForInterim(AuthState state) throws Exception {
		
		String url = SSO_URL + "/oauth2/v3/token";

		JsonObject json = new JsonObject();
		json.addProperty("grant_type", "authorization_code");
		json.addProperty("client_id", "ownerapi");
		json.addProperty("code", state.Code);
		json.addProperty("code_verifier", state.CodeVerifier);
		json.addProperty("redirect_uri", "https://auth.tesla.com/void/callback");

		WebRequests.Params params = new WebRequests.Params();
		params.setContentType("application/json");
		params.Body = new Gson().toJson(json);

		WebRequests.Response response = requests.fetch(url, params);
		if (!response.successful()) throw response.Ex;

		JsonParser parser = new JsonParser();
		JsonObject jsonResponse = parser.parse(response.Body).getAsJsonObject();
		
		state.AccessTokenInterim =
			jsonResponse.getAsJsonPrimitive("access_token").getAsString();
	}

	private void exchangeInterimForFinal(AuthState state) throws Exception {

		String url = OWNER_URL + "/oauth/token";

		JsonObject json = new JsonObject();
		json.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		json.addProperty("client_id", CLIENT_ID);
		json.addProperty("client_secret", CLIENT_SECRET);
		
		WebRequests.Params params = new WebRequests.Params();
		params.setContentType("application/json");
		params.addHeader("Authorization", "Bearer " + state.AccessTokenInterim);
		params.Body = new Gson().toJson(json);

		WebRequests.Response response = requests.fetch(url, params);
		if (!response.successful()) throw response.Ex;

		JsonParser parser = new JsonParser();
		JsonObject jsonResponse = parser.parse(response.Body).getAsJsonObject();
		
		state.AccessToken =
			jsonResponse.getAsJsonPrimitive("access_token").getAsString();
	}

	private void addAuthQueryStuff(WebRequests.Params params, AuthState state) {
		params.addQueryParam("client_id", "ownerapi");
		params.addQueryParam("code_challenge", state.CodeChallenge);
		params.addQueryParam("code_challenge_method", "S256");
		params.addQueryParam("redirect_uri", "https://auth.tesla.com/void/callback");
		params.addQueryParam("response_type", "code");
		params.addQueryParam("scope", "openid email offline_access");
		params.addQueryParam("state", state.State);
	}

	private String findQueryParam(String url, String name) {
		
		int ichQuestion = url.indexOf("?");

		for (String pair : url.substring(ichQuestion + 1).split("&")) {
			String[] kv = pair.split("=");
			if (Easy.urlDecode(kv[0]).equalsIgnoreCase(name)) {
				return(Easy.urlDecode(kv[1]));
			}
		}

		return(null);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private WebRequests requests;
	
	private String accessToken;

	private final static Logger log = Logger.getLogger(Tesla.class.getName());
}
