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
import com.google.gson.JsonParser;

public class OAuth2Login implements Closeable
{
	// +-----------------------+
	// | Configuration & Setup |
	// +-----------------------+
	
	public static class Config
	{
		public String ClientId;
		public String ClientSecret;

		public String Scope = "openid email";
		public String AuthURL = "https://accounts.google.com/o/oauth2/auth";
		public String TokenURL = "https://oauth2.googleapis.com/token";

		public String RedirectPath = "/__oauth2_redirect";

		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public OAuth2Login(Config cfg) throws Exception {
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

		public static State rehydrate(String dehydrated) {
			
			State state = new State();
			if (Easy.nullOrEmpty(dehydrated)) return(state);
			
			String[] fields = dehydrated.split("\\|");
			state.id = fields[0].equals("") ? null : fields[0];
			state.email = fields[1].equals("") ? null : fields[1];
			state.token = fields[2].equals("") ? null : fields[2];
			state.transitoryState = fields[3].equals("") ? null : fields[3];
			
			return(state);
		}

		public String dehydrate() {
			
			return(String.format("%s|%s|%s|%s",
								 id == null ? "" : id,
								 email == null ? "" : email,
								 token == null ? "" : token,
								 transitoryState == null ? "" : transitoryState));
		}
	}

	// +----------------------+
	// | getAuthenticationURL |
	// +----------------------+

	public String getAuthenticationURL(String baseURL, State state) {

		Map<String,String> queryParams = new HashMap<String,String>();
		queryParams.put("client_id", cfg.ClientId);
		queryParams.put("redirect_uri", Easy.urlPaste(baseURL, cfg.RedirectPath));
		queryParams.put("scope", cfg.Scope);
		queryParams.put("response_type", "code");
		queryParams.put("access_type", "online");

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

		JsonObject jsonResponse = parser.parse(body).getAsJsonObject();

		if (!jsonResponse.has("access_token")) return(false);
		state.token = jsonResponse.get("access_token").getAsString();

		if (!jsonResponse.has("id_token")) return(true);
		String idToken = jsonResponse.get("id_token").getAsString();
		String payloadEnc = idToken.split("\\.")[1];
		String payloadTxt = Easy.base64urlDecode(payloadEnc);
		JsonObject jsonIdToken = parser.parse(payloadTxt).getAsJsonObject();
		
		state.id = jsonIdToken.get("sub").getAsString();
		
		if (jsonIdToken.has("email")) {
			state.email = jsonIdToken.get("email").getAsString();
		}

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
