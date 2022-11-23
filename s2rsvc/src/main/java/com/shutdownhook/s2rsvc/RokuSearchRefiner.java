/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.MemoryCachingProxy;
import com.shutdownhook.toolbox.WebRequests;

public class RokuSearchRefiner implements RokuSearchInfo.Refiner 
{
	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		public String BaseUrl = "https://www.roku.com/api/v1/sow/search?query=";
		public String RokuChannelId = "151908";
		public MemoryCachingProxy.Config Cache = new MemoryCachingProxy.Config();
		public WebRequests.Config Requests = new WebRequests.Config();
		public boolean DebugPrintFetchBody = false;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public RokuSearchRefiner(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
		this.cache = new RokuCachingProxy(this);
		this.gson = new GsonBuilder().setPrettyPrinting().create();
	}

	public Config getConfig() {
		return(cfg);
	}

	// +------------------------+
	// | RokuSearchInfo.Refiner |
	// +------------------------+

	public void close() {
		requests.close();
	}

	public void refine(RokuSearchInfo info, UserChannelSet channels) throws Exception {

		if (info.Channel != null) return;
		
		RokuResult result = cache.getItem(info.Search);

		if (result != null &&
			result.content != null &&
			result.content.viewOptions != null) {

			for (RokuContentViewOption viewOption : result.content.viewOptions) {
				
				if (viewOption.channelId != null && channels.ok(viewOption.channelId)) {

					info.Channel = viewOption.channelId;

					if (info.Channel.equals(cfg.RokuChannelId) && result.content.meta != null) {
						info.ContentId = result.content.meta.id;
						info.MediaType = result.content.meta.mediaType;
					}
					
					log.info(String.format("RokuSearchRefiner found channel %s (%s/%s) for %s",
										   info.Channel, info.ContentId,
										   info.MediaType, info.Search));
					break;
				}
			}
		}
	}

	// +------------------+
	// | RokuCachingProxy |
	// +------------------+

	public static class RokuCachingProxy extends MemoryCachingProxy<String,RokuResult>
	{
		public RokuCachingProxy(RokuSearchRefiner refiner) throws Exception {
			super(refiner.getConfig().Cache);
			this.refiner = refiner;
		}

		@Override
		public RokuResult liveFetch(String query) throws Exception {
			return(refiner.searchBest(query));
		}

		private RokuSearchRefiner refiner;
	}

	// +-----------------+
	// | Roku Search API |
	// +-----------------+

	public static class RokuContentMeta
	{
		public String id;
		public String mediaType;
	}
	
	public static class RokuContentViewOption
	{
		public String channelId;
	}

	public static class RokuContent
	{
		public String title;
		public RokuContentViewOption[] viewOptions;
		public RokuContentMeta meta;
	}

	public static class RokuFeaturesSearch
	{
		public Double confidenceScore;
	}

	public static class RokuFeatures
	{
		public RokuFeaturesSearch search;
	}
	
	public static class RokuResult
	{
		public RokuContent content;
		public RokuFeatures features;
	}
	
	public static class RokuResults
	{
		RokuResult[] view;
	}

	public RokuResults search(String input) {

		RokuResults results = null;
		
		try {
			String fullUrl = cfg.BaseUrl + Easy.urlEncode(input);
			WebRequests.Response response = requests.fetch(fullUrl);
			
			if (!response.successful()) response.throwException(fullUrl);
			if (cfg.DebugPrintFetchBody) log.info(response.Body);

			results = gson.fromJson(response.Body, RokuResults.class);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "RokuSearchRefiner.search", true));
		}

		return(results);
	}

	public RokuResult searchBest(String input) {

		RokuResults results = search(input);
		if (results == null) return(null);

		for (RokuResult result : results.view) {
			try { if (result.features.search.confidenceScore == 1d) return(result); }
			catch (Exception e) { log.fine("no confidence score found; skipping"); }
		}

		return(null);
	}
	
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		RokuSearchRefiner refiner = null;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try {
			refiner = new RokuSearchRefiner(new RokuSearchRefiner.Config());

			if (args.length >= 2 && args[0].equalsIgnoreCase("best")) {

				RokuResult result = refiner.searchBest(args[1]);
				System.out.println(gson.toJson(result));
			}
			else {

				RokuResults results = refiner.search(args[0]);
				System.out.println(gson.toJson(results));
			}

		}
		finally {
			
			if (refiner != null) refiner.close();
		}
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private Gson gson; 
	private WebRequests requests;
	private RokuCachingProxy cache;

	private final static Logger log = Logger.getLogger(RokuSearchRefiner.class.getName());
}
