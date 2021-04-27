/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.smart;

import java.io.Closeable;
import java.lang.IllegalArgumentException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class SmartEhr implements Closeable
{
	// +------------------+
	// | Config and Setup |
	// +------------------+
	
	public static class Config
	{
		public String Scope = "patient/*.read";
		public List<SiteConfig> Sites = new ArrayList<SiteConfig>();
		public WebRequests.Config Requests = new WebRequests.Config();

		public Boolean LogFhirResponses = false; // careful as this will contain phi
	}

	public static class SiteConfig
	{
		// identifies a unique EHR
		public String SiteId;

		// ignore the inbound iss url and use this preconfigured one
		public String IssUrl; 
		public String ClientId;
		public String ClientSecret;

		// these urls can be null, if so we discover() them at init time
		public String AuthUrl;
		public String TokenUrl;
		public Boolean UseWellKnownMetadata;
	}

	public SmartEhr(Config cfg) throws Exception {

		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);

		sitesMap = new HashMap<String,SiteConfig>();
		for (SiteConfig site : cfg.Sites) {
			sitesMap.put(site.SiteId, site);
			if (site.AuthUrl == null) discover(site);
		}

		if (sitesMap.size() == 0) {
			log.severe("No sites found in config; clearly that's not right.");
		}

	}

	public void close() {
		requests.close();
	}

	// +---------+
	// | Session |
	// +---------+

	public static class Session
	{
		public UUID Id = UUID.randomUUID();
		public String SiteId;

		public String Launch;
		public Boolean NeedPatientBanner = false;

		public String AccessToken;
		public Instant AccessExpires;
		public String RefreshToken;

		public String PatientId;
		public String UserId;

		public transient boolean Updated = false;
		public transient SiteConfig Config = null;
	}

	public String dehydrate(Session session) {
		return(new Gson().toJson(session));
	}

	public String dehydrateIfUpdated(Session session) {
		return(session.Updated ? new Gson().toJson(session) : null);
	}

	public Session rehydrate(String json) throws Exception {

		Session session = new Gson().fromJson(json, Session.class);
		
		session.Config = sitesMap.get(session.SiteId);
		
		if (session.Config == null) {
			String msg = String.format("No match for site=%s id=%s",
									   session.SiteId, session.Id);
			
			throw new IllegalArgumentException(msg);
		}

		return(session);
	}
	
	// +-----------+
	// | 1. LAUNCH |
	// +-----------+

	// Implements logic for the "launch" endpoint configured in the EHR as the
	// URL that will load up your iframe or whatever UX. Call launch() passing
	// in the received parameters "siteid", "launch" and "iss". Remember the 
	// returned Session object for the caller, in a secure cookie or whatever
	// session mechanism (use dehydrate if not in-memory). Finally call
	// getAuthRedirectUrl and redirect the browser to that URL.
	//
	// Note "siteid" is NOT a standard SMART parameter; we use it as a key to
	// the ehr configuration and verify it with the configured iss parameter.
	// Each "launch url" configured with an ehr must embed the site id for
	// this code to work!

	public Session launch(String siteId, String launch, String iss) throws Exception {

		Session session = new Session();
		session.Id = UUID.randomUUID();
		session.Updated = true;
		session.SiteId = siteId;
		session.Launch = launch;
		
		session.Config = sitesMap.get(session.SiteId);
		
		if (session.Config == null) {
			throw new IllegalArgumentException("no match for " + siteId);
		}

		if (!session.Config.IssUrl.equals(iss)) {
			String msg = String.format("Provided iss %s doesn't match configured " +
									   "value for site %s (expected %s)",
									   iss, siteId, session.Config.IssUrl);
									   
			throw new IllegalArgumentException(msg);
		}

		return(session);
	}

	public String getAuthRequestUrl(Session session, String successfulReturnUrl) {

		Map<String,String> params = new HashMap<String,String>();
		
		params.put("response_type", "code");
		params.put("client_id", session.Config.ClientId);
		params.put("redirect_uri", successfulReturnUrl);
		params.put("launch", session.Launch);
		params.put("state", session.Id.toString());
		params.put("aud", session.Config.IssUrl);

		String scope = "launch openid fhirUser online_access";
		if (cfg.Scope != null) scope = scope + " " + cfg.Scope;

		params.put("scope", scope);
		
		return(Easy.urlAddQueryParams(session.Config.AuthUrl, params));
	}
	
	// +--------------------+
	// | 2. SUCCESSFUL AUTH |
	// +--------------------+

	// Implements logic for the endpoint hit by the browser after successful
	// authorization (as provided in the call to getAuthReuestUrl during launch).
	// Call successfulAuth() passing the received parameters "code" and "state",
	// as well as the url of the endpoint itself (which must be the same as
	// the "successfulReturnUrl" parameter used in getAuthRequestUrl).

	public void successfulAuth(Session session, String code, String state,
							   String successfulReturnUrlAgain) throws Exception {

		if (!session.Id.toString().equals(state)) {
			String msg = String.format("Provided state %s doesn't match session (%s)",
									   state,  session.Id.toString());
									   
			throw new IllegalArgumentException(msg);
		}

		// call ehr to get access_token
		
		Map<String,String> form = new HashMap<String,String>();
		form.put("grant_type", "authorization_code");
		form.put("code", code);
		form.put("redirect_uri", successfulReturnUrlAgain);
			
		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("text/javascript");
		params.setBasicAuth(session.Config.ClientId, session.Config.ClientSecret);
		params.setForm(form);

		WebRequests.Response response = requests.fetch(session.Config.TokenUrl, params);

		if (!response.successful()) {
			throw new Exception(String.format("Failed fetching access_token (%d) %s",
											  response.Status, response.Ex));
		}

		parseTokens(session, response.Body);

		if (session.PatientId == null || session.UserId == null) {
			throw new Exception("Missing patient and/or id_token fields in response!");
		}
	}

	// +----------------+
	// | 3. Data Access |
	// +----------------+

	public SmartTypes.Patient getPatient(Session session) throws Exception {
		String url = "/Patient/" + session.PatientId;
		String json = getJsonStr(session, session.Config.IssUrl, url);
		return(SmartTypes.Patient.fromJson(json));
	}

	public List<SmartTypes.Condition> getConditions(Session session)
		throws Exception {

		List<SmartTypes.Condition> conditions = new ArrayList<SmartTypes.Condition>();
		
		String url = "/Condition?patient=" + session.PatientId;
		JsonObject json = getJson(session, url);

		JsonArray entries = json.getAsJsonArray("entry");
		if (entries == null) {
			return(conditions);
		}
		
		for (int i = 0; i < entries.size(); ++i) {

			JsonElement c = entries.get(i).getAsJsonObject().get("resource");
			conditions.add(SmartTypes.Condition.fromJsonElement(c));
		}

		return(conditions);
	}

	public JsonObject getJson(Session session, String path) throws Exception {
		return(getJson(session, session.Config.IssUrl, path));
	}

	// +-------------------+
	// | Token Managerment |
	// +-------------------+

	private final static int REFRESH_BUFFER_SECONDS = (60 * 2); // two minutes
										   
	private void refreshTokenIfNeeded(Session session) throws Exception {

		if (session == null)
			return;
		
		Instant effectiveNow = Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS);
		if (effectiveNow.isBefore(session.AccessExpires)) {
			return;
		}

		if (session.RefreshToken == null) {
			log.warning("Token expired but no refresh available; expect failures!");
			return;
		}

		log.info("Refreshing EHR token for session " + session.Id.toString());

		Map<String,String> form = new HashMap<String,String>();
		form.put("grant_type", "refresh_token");
		form.put("refresh_token", "session.RefreshToken");
			
		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("text/javascript");
		params.setForm(form);

		WebRequests.Response response = requests.fetch(session.Config.TokenUrl, params);

		if (!response.successful()) {
			throw new Exception(String.format("Failed fetching refresh_token (%d) %s",
											  response.Status, response.Ex));
		}

		parseTokens(session, response.Body);
	}

	private void parseTokens(Session session, String responseJson) throws Exception {

		if (cfg.LogFhirResponses) {
			log.info(String.format("Token response:\n-----\n%s", responseJson));
		}

		JsonParser parser = new JsonParser();
		
		JsonObject json = parser.parse(responseJson).getAsJsonObject();
		
		session.AccessToken = json.getAsJsonPrimitive("access_token").getAsString();

		JsonPrimitive jsonPatient = json.getAsJsonPrimitive("patient");
		if (jsonPatient != null) {
			session.PatientId = jsonPatient.getAsString();
		}

		JsonPrimitive jsonRefresh = json.getAsJsonPrimitive("refresh_token");
		if (jsonRefresh != null) {
			session.RefreshToken = jsonRefresh.getAsString();
		}

		JsonPrimitive jsonExpires = json.getAsJsonPrimitive("expires_in");
		if (jsonExpires != null) {
			long secondsToExpiration = jsonExpires.getAsLong();
			session.AccessExpires = Instant.now().plusSeconds(secondsToExpiration);
		}

		JsonPrimitive jsonIdToken = json.getAsJsonPrimitive("id_token");
		if (jsonIdToken != null) {
			String tokenJson = Easy.base64Decode(jsonIdToken.getAsString().split("\\.")[1]);
			JsonObject jsonProfile = parser.parse(tokenJson).getAsJsonObject();
			session.UserId = jsonProfile.getAsJsonPrimitive("fhirUser").getAsString();
		}

		JsonPrimitive jsonBanner = json.getAsJsonPrimitive("need_patient_banner");
		session.NeedPatientBanner = (jsonBanner != null && jsonBanner.getAsBoolean());

		session.Updated = true;
	}

	// +--------------------+
	// | Metadata Discovery |
	// +--------------------+

	private void discover(SiteConfig site) throws Exception {
		
		if (site.UseWellKnownMetadata) {
			JsonObject json = getJson(null, site.IssUrl,
									  "/.well-known/smart-configuration");

			site.AuthUrl = json.getAsJsonPrimitive("authorization_endpoint").getAsString();
			site.TokenUrl = json.getAsJsonPrimitive("token_endpoint").getAsString();
		}
		else {
			JsonObject json = getJson(null, site.IssUrl, "/metadata");

			// ./rest[0]/security/extension[0].extension[]
			JsonArray extensions = json.getAsJsonArray("rest")
				.get(0).getAsJsonObject()
				.getAsJsonObject("security")
				.getAsJsonArray("extension")
				.get(0).getAsJsonObject()
				.getAsJsonArray("extension");

			for (int i = 0; i < extensions.size(); ++i) {

				JsonObject ext = extensions.get(i).getAsJsonObject();
				String tag = ext.getAsJsonPrimitive("url").getAsString();
				String val = ext.getAsJsonPrimitive("valueUri").getAsString();
				
				switch (tag) {
				    case "authorize": site.AuthUrl = val; break;
				    case "token": site.TokenUrl = val; break;
				}
			}
		}

		if (site.AuthUrl == null || site.TokenUrl == null) {
			throw new IllegalArgumentException("Unable to discover urls for " + site.SiteId);
		}
	}

	// +-----------------+
	// | Request Helpers |
	// +-----------------+

	private JsonObject getJson(Session session, String base, String path) throws Exception {
		String json = getJsonStr(session, base, path);
		return(new JsonParser().parse(json).getAsJsonObject());
	}

	private String getJsonStr(Session session, String base, String path) throws Exception {

		refreshTokenIfNeeded(session);
		
		String url = Easy.urlPaste(base, path);

		log.info(String.format("Fetching Fhir Url: %s (Session = %s)",
							   url, session == null ? "na" : session.Id));

		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("text/javascript");

		if (session != null) {
			params.addHeader("Authorization", "Bearer: " + session.AccessToken);
		}
		
		WebRequests.Response response = requests.fetch(url, params);

		if (!response.successful()) {
			throw new Exception(String.format("Failed fetching json (%d) %s",
											  response.Status, response.Ex));
		}

		if (cfg.LogFhirResponses) {
			log.info(String.format("Fhir response for %s:\n-----\n%s", url, response.Body));
		}
		
		return(response.Body);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Map<String,SiteConfig> sitesMap;
	private WebRequests requests;

	private final static Logger log = Logger.getLogger(SmartEhr.class.getName());
}

