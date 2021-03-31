/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.lang.System;
import java.io.*;
import java.net.*;
import java.util.logging.Logger;

public class YouTube
{
	private final static Logger log = Logger.getLogger(YouTube.class.getName());
	
	private final static String INFO_URL_FMT = "https://www.youtube.com/watch?v=%s";
	private final static String TITLE_MARKER = "name=\"title\" content=\"";
	private final static String DURATION_MARKER = "itemprop=\"duration\" content=\"";

	private final static int TIMEOUT_MS = 10000;
	
	public static VideoInfo getVideoInfo(String urlOrId) throws Exception {

		VideoInfo info = new VideoInfo();
		info.Id = parseUrlOrId(urlOrId);
		log.info("Resolved YT input " + urlOrId + " to " + info.Id);

		String url = String.format(INFO_URL_FMT, info.Id);
		String body = call(url);

		info.Title = extractMarkerToQuote(body, TITLE_MARKER);
		String duration8601 = extractMarkerToQuote(body, DURATION_MARKER);

		if (info.Title == null || duration8601 == null) {
			throw new Exception("Markers not found in YT response (" + url + ")");
		}
		
		log.info("YT Title=" + info.Title + "; Duration=" + duration8601);
		
		info.DurationSeconds = parse8601(duration8601);

		return(info);
	}

	private static String extractMarkerToQuote(String body, String marker) {

		int ichStart = body.indexOf(marker);
		if (ichStart == -1) return(null);

		ichStart += marker.length();
		int ichMac = body.indexOf("\"", ichStart);
		if (ichMac == -1) ichMac = marker.length();

		return(body.substring(ichStart, ichMac));
	}

	private static String call(String url) throws Exception {

		HttpURLConnection conn = null;
		InputStreamReader reader = null;
		BufferedReader buffered = null;

		try {
			conn = (HttpURLConnection) (new URL(url).openConnection());
			conn.setRequestMethod("GET");
			conn.setFollowRedirects(true);
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);

			int status = conn.getResponseCode();
			if (status < 200 || status >= 300) {
				String msg = String.format("Failed YT call (%d): %s", status,
										   conn.getResponseMessage());
				throw new Exception(msg);
			}

			StringBuilder sb = new StringBuilder();
			reader = new InputStreamReader(conn.getInputStream());
			buffered = new BufferedReader(reader);

			String line;
			while ((line = buffered.readLine()) != null) {
				sb.append(line).append("\n");
			}

			return(sb.toString());
		}
		finally {
			if (buffered != null) buffered.close();
			if (reader != null) reader.close();
			if (conn != null) conn.disconnect();
		}
	}

	private static String parseUrlOrId(String urlOrId) {

		String[] firstSplit = urlOrId.split("\\?");
		if (firstSplit.length == 1) return(urlOrId);

		for (String kv : firstSplit[1].split("&")) {
			if (kv.startsWith("v=")) return(kv.substring(2));
		}

		return(urlOrId);
	}

	private static int parse8601(String dur8601) {

		// Turn into H:M:S ints by crazy-ugly string hacking
		String readyToSplit =
			dur8601.replace("PT","").replace("H", ":").replace("M", ":").replace("S", "");

		String[] parts = readyToSplit.split(":");

		int seconds = Integer.parseInt(parts[parts.length-1]); // secs
		if (parts.length > 1) seconds += (Integer.parseInt(parts[parts.length-2]) * 60); // mins
		if (parts.length > 2) seconds += (Integer.parseInt(parts[parts.length-3]) * 3600); // hrs

		return(seconds);
	}

	public static class VideoInfo
	{
		public String Id;
		public String Title;
		public int DurationSeconds;
	}
}
