/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.tvdb;

import java.io.Closeable;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.shutdownhook.s2rsvc.tvdb.Model.Episode;
import com.shutdownhook.s2rsvc.tvdb.Model.Series;
import com.shutdownhook.s2rsvc.tvdb.Model.ShortUrlInfo;

public class Api implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		public String ApiKey;
		public String ApiUrl = "https://api4.thetvdb.com/v4";
		public WebRequests.Config Requests = new WebRequests.Config();
		public Boolean DebugPrintFetchBody = false;
	}

	public Api(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.parser = new JsonParser();

		// for short url requests we do NOT want to follow redirects;
		// we want to get the real url through the 302
		WebRequests.Config shortUrlCfg = new WebRequests.Config();
		shortUrlCfg.FollowRedirects = false;
		this.shortUrlRequests = new WebRequests(shortUrlCfg);
	}

	public void close() {
		shortUrlRequests.close();
		requests.close();
	}

	// +-------------+
	// | getShortUrl |
	// +-------------+

	public ShortUrlInfo getShortUrl(String url) throws Exception {
		
		WebRequests.Response response = shortUrlRequests.fetch(url);
		if (response.Status == 404) return(null);
		
		if (response.Status != 302) {
			response.throwException(url);
		}

		String redirectUrl = response.Headers.get("Location").get(0);

		return(parseUrl(url, redirectUrl));
	}

	private ShortUrlInfo parseUrl(String shortUrl, String redirectUrl)
		throws Exception {

		ShortUrlInfo info = new ShortUrlInfo();
		info.Url = shortUrl;
		info.Created = Instant.now();

		String cleanUrl = redirectUrl.trim().toLowerCase();
		int cch = cleanUrl.length();

		info.EpisodeId = findMarkedInt(cleanUrl, cch, "/episode/");
		info.SeriesId = findMarkedInt(cleanUrl, cch, "/show/");
		info.MovieId = findMarkedString(cleanUrl, cch, "/movie/");

		return(info);
	}

	private Integer findMarkedInt(String url, int cch, String marker) {

		int ich = url.indexOf(marker);
		if (ich == -1) return(null);

		ich += marker.length();
		int num = 0;
		
		while (ich < cch && Character.isDigit(url.charAt(ich))) {
			num *= 10;
			num += Character.getNumericValue(url.charAt(ich));
			++ich;
		}

		return(num);
	}

	private String findMarkedString(String url, int cch, String marker) {

		int ich = url.indexOf(marker);
		if (ich == -1) return(null);

		ich += marker.length();
		
		int ichWalk = ich + 1;
		while (ichWalk < cch &&
			   url.charAt(ichWalk) != '?' &&
			   url.charAt(ichWalk) != '/') {
			++ichWalk;
		}

		return(url.substring(ich, ichWalk));
	}

	// +--------+
	// | Series |
	// +--------+

	private static String SERIES_URL_FMT =
		"/series/%d/extended?meta=translations&short=true";
	
	public Series getSeries(int id) throws Exception {
		ensureAuthentication();
		String url = String.format(SERIES_URL_FMT, id);
		return(seriesFromJson(fetch(url, null)));
	}

	private Series seriesFromJson(JsonObject json) throws Exception {
		Series s = new Series();

		JsonObject jsonData = json.getAsJsonObject("data");
		s.Id = jsonData.get("id").getAsInt();
		s.Created = Instant.now();
		s.Name = jsonData.get("name").getAsString();

		if (jsonData.has("latestNetwork")) {
			s.NetworkId = jsonData.getAsJsonObject("latestNetwork")
				.get("id").getAsString();
		}
		else if (jsonData.has("originalNetwork")) {
			s.NetworkId = jsonData.getAsJsonObject("originalNetwork")
				.get("id").getAsString();
		}
			
		return(s);
	}
	
	// +---------+
	// | Episode |
	// +---------+

	private static String EPISODE_URL_FMT = "/episodes/%d";
	
	public Episode getEpisode(int id, boolean includeSeries) throws Exception {
		ensureAuthentication();
		String url = String.format(EPISODE_URL_FMT, id);
		return(episodeFromJson(fetch(url, null), includeSeries));
	}

	private Episode episodeFromJson(JsonObject json, boolean includeSeries)
		throws Exception {
		
		Episode e = new Episode();

		JsonObject jsonData = json.getAsJsonObject("data");
		e.Id = jsonData.get("id").getAsInt();
		e.Created = Instant.now();

		e.SeriesId = getNullableInt(jsonData, "seriesId");
		e.Season = getNullableInt(jsonData, "seasonNumber");
		e.Number = getNullableInt(jsonData, "number");

		if (includeSeries && e.SeriesId != null) {
			try { e.Series = getSeries(e.SeriesId); }
			catch (Exception ex) { /* eat it */ }
		}

		return(e);
	}

	// +----------------------+
	// | ensureAuthentication |
	// +----------------------+

	private void ensureAuthentication() throws Exception {
		
		if (token != null) return;

		JsonObject jsonRequest = new JsonObject();
		jsonRequest.addProperty("apikey", cfg.ApiKey);
		
		JsonObject jsonResponse = fetch("login", jsonRequest);
		token = jsonResponse.getAsJsonObject("data").get("token").getAsString();
	}

	// +-------+
	// | fetch |
	// +-------+
	
	private JsonObject fetch(String url, JsonObject json) throws Exception {

		String fullUrl = Easy.urlPaste(cfg.ApiUrl, url);

		WebRequests.Params params = new WebRequests.Params();

		if (json != null) {
			params.setContentType("application/json");
			params.Body = gson.toJson(json);
		}

		if (token != null) {
			params.addHeader("Authorization", "Bearer " + token);
		}

		WebRequests.Response response = requests.fetch(fullUrl, params);
		if (!response.successful()) response.throwException(fullUrl);

		if (cfg.DebugPrintFetchBody) {
			System.out.println(response.Body);
		}
		
		JsonObject jsonResponse = parser.parse(response.Body).getAsJsonObject();
		String status = jsonResponse.get("status").getAsString().toLowerCase();

		if (!"success".equals(status)) {
			if (jsonResponse.has("message")) {
				status = status + ": " + jsonResponse.get("message");
			}

			throw new Exception(status);
		}

		return(jsonResponse);
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	public static Integer getNullableInt(JsonObject json, String name) {
		if (!json.has(name)) return(null);
		JsonElement elt = json.get(name);
		if (elt.isJsonNull()) return(null);
		return(elt.getAsInt());
	}
	
	public static String getNullableString(JsonObject json, String name) {
		if (!json.has(name)) return(null);
		JsonElement elt = json.get(name);
		if (elt.isJsonNull()) return(null);
		return(elt.getAsString());
	}

	private Config cfg;
	private WebRequests requests;
	private WebRequests shortUrlRequests;
	private Gson gson;
	private JsonParser parser;
	private String token;

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String args[]) throws Exception {

		Easy.setSimpleLogFormat("INFO");

		int cargs = args.length;
		String action = (cargs >= 1 ? args[0].toLowerCase() : null);

		if ("shorturl".equals(action)) {
			if (cargs != 2) {
				System.err.println("ShortUrl action requires an input url");
				return;
			}

			doShortUrl(args[1]);
			return;
		}

		if (cargs < 2) {
			System.err.println("Action " + action + " requires an api key");
			return;
		}
		
		Config cfg = new Config();
		cfg.ApiKey = args[1];
		Api api = new Api(cfg);

		if ("getmap".equals(action)) {
			getMap(api);
		}
		else if (cargs < 3) {
			System.err.println("series / episode requires query id");
		}
		else {
			int id = Integer.parseInt(args[2]);
		
			System.out.println(args[0] + ": " +
							   (args[0].toLowerCase().equals("series")
								? api.getSeries(id).toString()
								: api.getEpisode(id, true).toString()));
		}
		
		api.close();
	}

	private static void getMap(Api api) throws Exception {

		api.ensureAuthentication();

		boolean hasNext = true;
		int page = 0;

		while (hasNext) {
			
			String url = String.format("/companies?page=%d", page++);
			JsonObject jsonResult = api.fetch(url, null);

			JsonArray jsonCompanies = jsonResult.getAsJsonArray("data");
			for (int i = 0; i < jsonCompanies.size(); ++i) {
				
				JsonObject jsonCompany = jsonCompanies.get(i).getAsJsonObject();
				
				Integer companyType = getNullableInt(jsonCompany, "primaryCompanyType");
				if (companyType == null && jsonCompany.has("companyType")) {

					companyType = getNullableInt(jsonCompany.getAsJsonObject("companyType"),
												 "companyTypeId");
				}

				if (companyType != null && companyType == 1) {
					System.out.println(String.format("%d\t%s\t%s",
										 jsonCompany.get("id").getAsInt(),
										 getNullableString(jsonCompany, "name"),
										 getNullableString(jsonCompany, "country")));
				}
			}

			hasNext = (jsonCompanies.size() > 0 &&
					   jsonResult.has("links") &&
					   jsonResult.getAsJsonObject("links").has("next") &&
					   !jsonResult.getAsJsonObject("links").get("next").isJsonNull());
		}
	}

	private static void doShortUrl(String url) throws Exception {

		Api api = new Api(new Config());
		ShortUrlInfo info = api.getShortUrl(url);

		System.out.println(url + " ===> " + info.toString());

		api.close();
	}
}

