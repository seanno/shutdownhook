/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.wiki;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Worker;

import com.shutdownhook.s2rsvc.SearchParser.ParsedSearch;

public class WikiShows implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		public Map<String,String> StreamingServiceUrls;
		public Integer RefreshIntervalSeconds = 60 * 60 * 12; // every 12 hours
		public Integer StopWaitSeconds = 10;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public WikiShows(Config cfg) throws Exception {
		this.cfg = cfg;
		this.showLock = new Object();
		
		this.refreshThread = new WikiShowsRefreshThread(this);
		this.refreshThread.go();
	}

	public void close() {
		this.refreshThread.waitForStop(cfg.StopWaitSeconds);
	}

	public Config getConfig() {
		return(cfg);
	}
	
	// +-------------+
	// | parseSearch |
	// +-------------+

	public ParsedSearch parseSearch(String input) throws Exception {

		String channel = lookup(input);
		if (channel == null) return(null);

		ParsedSearch srch = new ParsedSearch();
		srch.Search = input;
		srch.Channel = channel;

		return(srch);
	}

	// +--------+
	// | lookup |
	// +--------+

	public String lookup(String query) {
		
		Map<String,String> localShowMap = null;
		synchronized(showLock) { localShowMap = showMap; }

		// if we haven't generated a map yet, this is effectively a
		// noop. This will only happen right at startup or if there's
		// some catasrophic error; better to skip than to block requests
		// on what could be a long haul 
		
		if (localShowMap == null) {
			log.info("No local show map (yet); bailing out");
			return(null);
		}
		
		return(localShowMap.get(query.toLowerCase()));
	}

	// +------------------------+
	// | WikiShowsRefreshThread |
	// +------------------------+

	public static class WikiShowsRefreshThread extends Worker
	{
		public WikiShowsRefreshThread(WikiShows wikiShows) {
			this.wikiShows = wikiShows;
		}
		
		public void work() throws Exception {

			do {
				try {
					wikiShows.updateShowMap();
				}
				catch (Exception e) {
					log.severe(Easy.exMsg(e, "wikiShowsRefreshThread", true));
				}
			}
			while (!sleepyStop(wikiShows.getConfig().RefreshIntervalSeconds));
		}
		
		public void cleanup(Exception e) {
			// nut-n-honey
		}

		private WikiShows wikiShows;
	}

	// +-----------------+
	// | generateShowMap |
	// +-----------------+

	public void updateShowMap() throws Exception {
		log.info("fetching show map");
		Map<String,String> newShowMap = generateShowMap();
		log.info(String.format("%d shows added", newShowMap.size()));
		synchronized(showLock) { showMap = newShowMap; }
	}

	private Map<String,String> generateShowMap() throws IOException {

		Map<String,String> showMap = new HashMap<String,String>();

		for (String appId : cfg.StreamingServiceUrls.keySet()) {
			addShows(showMap, appId, cfg.StreamingServiceUrls.get(appId));
		}

		return(showMap);
	}

	private void addShows(Map<String,String> showMap,
						  String appId, String url) throws IOException {

		Document doc = Jsoup.connect(url).get();
		log.info("Parsed " + url + " ok: " + doc.title());

		for (Element table : doc.select("table.wikitable")) {
			for (Element row : table.select("tr")) {
				Element cell = row.firstElementChild(); // TD or TH
				if (cell != null) {
					Element i = cell.selectFirst("i");
					if (i != null) showMap.put(i.text().toLowerCase().trim(), appId);
				}
			}
		}
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private WikiShowsRefreshThread refreshThread;

	private Map<String,String> showMap;
	private Object showLock;
	
	private final static Logger log = Logger.getLogger(WikiShows.class.getName());
}