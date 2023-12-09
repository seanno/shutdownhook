/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
**
** !!! Note the functions in this class require Google's GSON library to be on the CLASSPATH.
** !!! https://mvnrepository.com/artifact/com.google.code.gson/gson (built with v2.8.6)
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class OAuth2Login implements Closeable
{
	// +-----------+
	// | Providers |
	// +-----------+

	public static final String PROVIDER_GOOGLE = "google";
	public static final String PROVIDER_FACEBOOK = "facebook";
	public static final String PROVIDER_GITHUB = "github";
	public static final String PROVIDER_GITHUB_REAUTH = "github_reauth";
	public static final String PROVIDER_AMAZON = "amazon";
	public static final String PROVIDER_OTHER = "other";

	public static final String GITHUB_USER_API = "https://api.github.com/user";
	public static final String GITHUB_EMAILS_API = "https://api.github.com/user/emails";
	public static final String AMAZON_PROFILE_API = "https://api.amazon.com/user/profile";

	public static final Map<String,ProviderInfo> PROVIDER_MAP =
		new HashMap<String,ProviderInfo>();

	static {
		PROVIDER_MAP.put(PROVIDER_GOOGLE, new ProviderInfo(
		    "https://accounts.google.com/o/oauth2/auth",
			"https://oauth2.googleapis.com/token",
			"openid email"));

		PROVIDER_MAP.put(PROVIDER_FACEBOOK, new ProviderInfo(
			"https://www.facebook.com/v11.0/dialog/oauth",
			"https://graph.facebook.com/v11.0/oauth/access_token",
			"openid email"));
		
		PROVIDER_MAP.put(PROVIDER_GITHUB, new ProviderInfo(
			"https://github.com/login/oauth/authorize",
			"https://github.com/login/oauth/access_token",
			"user:email"));

		PROVIDER_MAP.put(PROVIDER_GITHUB_REAUTH, new ProviderInfo(
			"https://github.com/login/oauth/authorize?prompt=consent",
			"https://github.com/login/oauth/access_token",
			"user:email"));

		PROVIDER_MAP.put(PROVIDER_AMAZON, new ProviderInfo(
			"https://www.amazon.com/ap/oa",
			"https://api.amazon.com/auth/o2/token",
			"profile"));

		PROVIDER_MAP.put(PROVIDER_OTHER, new ProviderInfo(
			null,
			null,
			"openid email"));
	}

	public static class ProviderInfo
	{
		public ProviderInfo(String authURL, String tokenURL, String scope) {
			this.AuthURL = authURL;
			this.TokenURL = tokenURL;
			this.Scope = scope;
		}
		
		public String AuthURL;
		public String TokenURL;
		public String Scope;
	}

	// +-----------------------+
	// | Configuration & Setup |
	// +-----------------------+
	
	public static class Config
	{
		public String ClientId;
		public String ClientSecret;

		// See above for provider labels (case sensitive!). If using "other",
		// you MUST specify AuthURL/TokenURL/Scope. Otherwise these values are
		// set using PROVIDER_MAP iff they are null in the provider config. This
		// override behavior is most useful to set a different Scope, e.g., if
		// you want to use the returned token to make provider-specific API calls.

		public String Provider = "google";
		public String AuthURL;
		public String TokenURL;
		public String Scope;

		public String RedirectPath = "/__oauth2_redirect";
		public String LogoutPath = "/__oauth2_logout";

		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public OAuth2Login(Config cfg) throws Exception {
		
		ProviderInfo info = PROVIDER_MAP.get(cfg.Provider);
		if (info != null) {
			if (cfg.AuthURL == null) cfg.AuthURL = info.AuthURL;
			if (cfg.TokenURL == null) cfg.TokenURL = info.TokenURL;
			if (cfg.Scope == null) cfg.Scope = info.Scope;
		}
		
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
		this.parser = new JsonParser();
	}

	public void close() {
		requests.close();
	}

	// +--------------+
	// | OAuth2.State |
	// +--------------+

	public static class State
	{
		public String getId() { return(id); }
		public String getEmail() { return(email); }
		public String getToken() { return(token); }
		public boolean isAuthenticated() { return(token != null); }
		
		private String id;
		private String email;
		private String token;
		private String transitoryState;
		private String transitoryRedirect;

		public static State rehydrate(String dehydrated) {
			
			State state = new State();
			if (Easy.nullOrEmpty(dehydrated)) return(state);
			
			String[] fields = dehydrated.split("\\|");
			int c = fields.length;
			
			state.id = fields[0].equals("") ? null : fields[0];
			state.email = (c < 2 || fields[1].equals("")) ? null : fields[1];
			state.token = (c < 3 || fields[2].equals("")) ? null : fields[2];
			state.transitoryState = (c < 4 || fields[3].equals("")) ? null : fields[3];
			state.transitoryRedirect = (c < 5 || fields[4].equals("")) ? null : fields[4];
			
			return(state);
		}

		public String dehydrate() {
			
			return(String.format("%s|%s|%s|%s|%s",
					 id == null ? "" : id,
					 email == null ? "" : email,
					 token == null ? "" : token,
					 transitoryState == null ? "" : transitoryState,
					 transitoryRedirect == null ? "" : transitoryRedirect));
		}

		public String popTransitoryRedirect() {
			String targetURL = (transitoryRedirect == null ? "/" : transitoryRedirect);
			transitoryRedirect = null;
			return(targetURL);
		}
	}

	// +----------------------+
	// | getAuthenticationURL |
	// +----------------------+

	public String getAuthenticationURL(String baseURL, String targetURL, State state) {

		Map<String,String> queryParams = new HashMap<String,String>();
		queryParams.put("client_id", cfg.ClientId);
		queryParams.put("redirect_uri", Easy.urlPaste(baseURL, cfg.RedirectPath));
		queryParams.put("scope", cfg.Scope);
		queryParams.put("response_type", "code");
		queryParams.put("access_type", "online");

		state.transitoryRedirect = targetURL;
		state.transitoryState = new BigInteger(130, new SecureRandom()).toString(32);
		queryParams.put("state", state.transitoryState);

		return(Easy.urlAddQueryParams(cfg.AuthURL, queryParams));
	}
	
	// +-----------------+
	// | handleReturnURL |
	// +-----------------+

	// Returns an error string IFF unsuccessful, else null
	
	public String handleReturnURL(String baseURL, String returnURL, State state) {

		Map<String,String> queryParams = extractQueryParams(returnURL);

		// error cases
		
		String error = queryParams.get("error");
		if (!Easy.nullOrEmpty(error)) {
			return(error);
		}

		String returnedState = queryParams.get("state");
		if (returnedState == null || !returnedState.equals(state.transitoryState)) {
			return("Invalid state");
		}

		state.transitoryState = null;

		// swap code for tokens
		
		String code = queryParams.get("code");
		if (Easy.nullOrEmpty(code)) {
			return("Missing code");
		}

		Map<String,String> form = new HashMap<String,String>();
		form.put("client_id", cfg.ClientId);
		form.put("client_secret", cfg.ClientSecret);
		form.put("code", code);
		form.put("grant_type", "authorization_code");
		form.put("redirect_uri", Easy.urlPaste(baseURL, cfg.RedirectPath));
		
		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("application/json");
		params.setForm(form);

		WebRequests.Response response = requests.fetch(cfg.TokenURL, params);
		if (!response.successful()) {
			return(String.format("%s (%d)", response.StatusText, response.Status));
		}

		// parse the tokens

		if (!parseTokenJSON(response.Body, state)) {
			return("Failed parsing token response");
		}

		// whew! state is now updated.
		return(null);
	}

	private Map<String,String> extractQueryParams(String returnURL) {

		int ichQuestion = returnURL.indexOf("?");
		if (ichQuestion == -1) return(new HashMap<String,String>());

		return(Easy.parseQueryString(returnURL.substring(ichQuestion + 1)));
	}

	private boolean parseTokenJSON(String body, State state) {

		//log.info(">>>TOKEN>>>\n" + body + "\n<<<");
		JsonObject jsonResponse = parser.parse(body).getAsJsonObject();

		if (!hasNonNull(jsonResponse, "access_token")) return(false);
		state.token = jsonResponse.get("access_token").getAsString();

		if (cfg.Provider.equals(PROVIDER_GITHUB) ||
			cfg.Provider.equals(PROVIDER_GITHUB_REAUTH)) {
			fetchGithubInfo(state);
		}
		else if (cfg.Provider.equals(PROVIDER_AMAZON)) {
			fetchAmazonInfo(state);
		}
		else if (hasNonNull(jsonResponse, "id_token")) {
			parseIdToken(jsonResponse.get("id_token").getAsString(), state);
		}

		return(true);
	}

	private void parseIdToken(String idToken, State state) {

		String payloadEnc = idToken.split("\\.")[1];
		String payloadTxt = Easy.base64urlDecode(payloadEnc);
		//log.info(">>>PAYLOAD>>>\n" + payloadTxt + "\n<<<");
		JsonObject jsonIdToken = parser.parse(payloadTxt).getAsJsonObject();

		state.id = jsonIdToken.get("sub").getAsString();
		if (hasNonNull(jsonIdToken, "email")) state.email = jsonIdToken.get("email").getAsString();
	}

	private boolean fetchGithubInfo(State state) {

		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("application/vnd.github+json");
		params.addHeader("Authorization", "Bearer " + state.getToken());

		WebRequests.Response response = requests.fetch(GITHUB_USER_API, params);
		if (!response.successful()) return(false);

		JsonObject jsonUser = parser.parse(response.Body).getAsJsonObject();
		state.id = jsonUser.get("login").getAsString();

		if (hasNonNull(jsonUser, "email")) {
			// if user has made their email public, easy short-circuit
			state.email = jsonUser.get("email").getAsString();
		}
		else {
			// otherwise look in here
		    response = requests.fetch(GITHUB_EMAILS_API, params);
			if (!response.successful()) return(false);

			JsonArray jsonEmails = parser.parse(response.Body).getAsJsonArray();
			if (jsonEmails.size() == 0) return(true);

			// default to first email if none are "primary"
			state.email = jsonEmails.get(0).getAsJsonObject().get("email").getAsString();
			
			for (int i = 0; i < jsonEmails.size(); ++i) {
				if (jsonEmails.get(i).getAsJsonObject().get("primary").getAsBoolean()) {
					state.email = jsonEmails.get(i).getAsJsonObject().get("email").getAsString();
					break;
				}
			}
		}

		return(true);
	}

	private boolean fetchAmazonInfo(State state) {

		WebRequests.Params params = new WebRequests.Params();
		params.setAccept("application/json");
		params.addHeader("Authorization", "Bearer " + state.getToken());

		WebRequests.Response response = requests.fetch(AMAZON_PROFILE_API, params);
		if (!response.successful()) return(false);

		JsonObject jsonProfile = parser.parse(response.Body).getAsJsonObject();
		state.id = jsonProfile.get("user_id").getAsString();
		state.email = jsonProfile.get("email").getAsString();

		return(true);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private boolean hasNonNull(JsonObject json, String field) {
		if (!json.has(field)) return(false);
		if (json.get(field) == null) return(false);
		if (json.get(field).isJsonNull()) return(false);
		return(true);
	}

	// +---------+
	// | Members |
	// +---------+
	
	private Config cfg;
	private WebRequests requests;
	private JsonParser parser;
	
	private final static Logger log = Logger.getLogger(OAuth2Login.class.getName());
}
