//
// WEBRESOURCE.JAVA
//
// params: url = url to check
//         (optional) infoUrl = url for resource details (if empty use url)
//         (optional) acceptType = Accept header to send
//         (optional) postData = content to POST (if not present will GET)
//         (optional) contentType = if POST, Content-Type header to send
//         (optional) timeoutSeconds = seconds to wait for response (default 2 min)
//         (optional) search = string that must be present in returned content

package com.shutdownhook.backstop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Timey;
import com.shutdownhook.toolbox.WebRequests;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class WebResource implements Checker
{
	private final static int DEFAULT_TIMEOUT = (2 * 60); // 2 minutes in seconds

	private final static int CCH_RESULT_TRUNC = 150;
	
	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  String stateId,
					  List<Status> statuses) throws Exception {

		String url = params.get("url");
		if (Easy.nullOrEmpty(url)) throw new Exception("WebResource requires url parameter");

		String infoUrl = params.get("infoUrl");
		if (Easy.nullOrEmpty(infoUrl)) infoUrl = url;

		WebRequests.Params reqParams = new WebRequests.Params();
		
		String acceptType = params.get("acceptType");
		if (!Easy.nullOrEmpty(acceptType)) reqParams.addHeader("Accept", acceptType);

		String contentType = params.get("contentType");
		if (!Easy.nullOrEmpty(contentType)) reqParams.addHeader("Content-Type", contentType);

		String postData = params.get("postData");
		if (!Easy.nullOrEmpty(postData)) reqParams.Body = postData;
		
		String timeoutStr = params.get("timeoutSeconds");
		int timeout = (Easy.nullOrEmpty(timeoutStr) ? DEFAULT_TIMEOUT : Integer.parseInt(timeoutStr));

		long timer = Timey.startTimer();
		
		WebRequests.Response response =
			helpers.getRequests()
			.fetchAsync(url, reqParams)
			.get(timeout, TimeUnit.SECONDS);

		long millis = Timey.stopTimerMillis(timer);
		
		if (!response.successful()) {
			statuses.add(new Status("", StatusLevel.ERROR, trunc(response.toString()), infoUrl));
			return;
		}

		String search = params.get("search");
		if (!Easy.nullOrEmpty(search) && response.Body.indexOf(search) == -1) {
			String msg = String.format("\"%s\" not found in body: %s", search, trunc(response.Body));
			statuses.add(new Status("", StatusLevel.ERROR, msg, infoUrl));
			return;
		}

		String msg = String.format("%d ms response", millis);
		statuses.add(new Status("", StatusLevel.OK, msg, infoUrl));
	}

	private String trunc(String input) {
		if (input == null) return("");
		if (input.length() > CCH_RESULT_TRUNC) return(input.substring(0, CCH_RESULT_TRUNC) + "...");
		return(input);
	}
}
