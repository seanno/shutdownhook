//
// SESSIONBROWSER.JAVA
//
// THIS IS VERY BASIC! It's just made for simple session cookie persistence.
//

package com.shutdownhook.backstop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.shutdownhook.toolbox.WebRequests;

public class SessionBrowser
{
	private static String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";
	
	public SessionBrowser(WebRequests requests) {
		this.requests = requests;
		this.cookies = new HashMap<String,String>();
	}

	// +-----------+
	// | getParams |
	// +-----------+

	public WebRequests.Params getParams() {
		WebRequests.Params params = new WebRequests.Params();
		params.addHeader("User-Agent", USER_AGENT);
		addCookies(params);
		return(params);
	}

	// +-------+
	// | fetch |
	// +-------+

	public WebRequests.Response fetch(String url, String tag) throws Exception {
		return(fetch(url, getParams(), tag));
	}

	public WebRequests.Response fetch(String url, WebRequests.Params params, String tag) throws Exception {

		if (!params.Headers.containsKey("Origin")) {
			int ichDoubleSlashes = url.indexOf("//");
			int ichNextSlash = url.indexOf("/", ichDoubleSlashes + 2);
			params.addHeader("Origin", url.substring(0, ichNextSlash));
		}

		WebRequests.Response response = requests.fetch(url, params);
		if (response.Ex != null) response.throwException(tag);

		rememberCookies(response);
		return(response);
	}
	
	// +---------+
	// | Cookies |
	// +---------+

	private void rememberCookies(WebRequests.Response response) {

		List<String> setCookies = response.Headers.get("Set-Cookie");
		if (setCookies == null || setCookies.size() == 0) return;
		
		for (String setCookie : setCookies) {
			
			int ichEquals = setCookie.indexOf("=");
			if (ichEquals == -1) return;
			
			int ichSemi = setCookie.indexOf(";", ichEquals);
			if (ichSemi == -1) return;

			cookies.put(setCookie.substring(0, ichEquals),
						setCookie.substring(ichEquals + 1, ichSemi));
		}
	}
	
	private void addCookies(WebRequests.Params params) {

		StringBuilder sb = new StringBuilder();
		for (String name : cookies.keySet()) {
			if (sb.length() > 0) sb.append("; ");
			sb.append(name).append("=").append(cookies.get(name));
		}

		params.addHeader("Cookie", sb.toString());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private WebRequests requests;
	private Map<String,String> cookies;
}
