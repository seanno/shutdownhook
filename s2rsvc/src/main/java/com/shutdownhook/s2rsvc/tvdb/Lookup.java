/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.tvdb;

import java.io.Closeable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.CachingProxy;

import com.shutdownhook.s2rsvc.RokuSearchInfo;
import com.shutdownhook.s2rsvc.tvdb.Model.Episode;
import com.shutdownhook.s2rsvc.tvdb.Model.Series;
import com.shutdownhook.s2rsvc.tvdb.Model.ShortUrlInfo;

public class Lookup implements RokuSearchInfo.Parser
{
	// +----------------+
	// | Config & Setup |
	// +----------------+
	
	public static class Config
	{
		public DB.Config DB = new DB.Config();
		public Api.Config Api = new Api.Config();
		public CachingProxy.Config EpisodeProxy = new CachingProxy.Config();
		public CachingProxy.Config SeriesProxy = new CachingProxy.Config();
		public CachingProxy.Config ShortUrlProxy = new CachingProxy.Config();

		public Map<String,String> RokuChannelMap;

		public Integer ShutdownWaitSeconds = 10;
		public Boolean IncludeStackTraces = true;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Lookup(Config cfg) throws Exception {
		this.cfg = cfg;
		this.db = new DB(cfg.DB);
		this.api = new Api(cfg.Api);

		this.episodeProxy = new EpisodeCachingProxy(cfg.EpisodeProxy, db, api);
		this.seriesProxy = new SeriesCachingProxy(cfg.SeriesProxy, db, api);
		this.shortUrlProxy = new ShortUrlCachingProxy(cfg.ShortUrlProxy, db, api);
	}

	// +-----------------------+
	// | RokuSearchInfo.Parser |
	// +-----------------------+

	public void close() {
		episodeProxy.close();
		seriesProxy.close();
		shortUrlProxy.close();
		api.close();
	}

	public RokuSearchInfo parse(String input) throws Exception {

		// If we find a TV Time URL, figure out series/episode/etc.
		// If we don't know what it is, return NULL to move on.

		int ich = input.indexOf(TVTIME_URL_MARKER);
		if (ich == -1) return(null);

		int cch = input.length();
		int ichWalk = ich + 1;
		while (ichWalk < cch && !Character.isWhitespace(input.charAt(ichWalk))) {
			++ichWalk;
		}

		String url = input.substring(ich, ichWalk);
		log.fine("TVDB Lookup found URL: " + url);
		
		ShortUrlInfo info = getShortUrlInfo(url);
		if (info == null) return(null);

		RokuSearchInfo rokuInfo = null;

		// query by episode 
		if (info.EpisodeId != null) {
			Episode e = getEpisode(info.EpisodeId);
			if (e != null) {
				Series s = getSeries(e.SeriesId);
				if (s != null) {
					rokuInfo = new RokuSearchInfo();
					rokuInfo.Search = s.Name;
					rokuInfo.Season = e.Season.toString();
					rokuInfo.Number = e.Number.toString();
					rokuInfo.Channel = findRokuChannel(s.NetworkId);
				}
			}
		}
		
		// query by series
		if (rokuInfo == null && info.SeriesId != null) {
			Series s = getSeries(info.SeriesId);
			if (s != null) {
				rokuInfo = new RokuSearchInfo();
				rokuInfo.Search = s.Name;
				rokuInfo.Channel = findRokuChannel(s.NetworkId);
			}
		}

		// neither of those worked but it is our url, see what we can salvage
		if (rokuInfo == null) {
			int ichMarker = input.toLowerCase().indexOf(ON_TVTIME_MARKER);
			if (ichMarker != -1) {
				rokuInfo = new RokuSearchInfo();
				rokuInfo.Search = input.substring(0, ichMarker).trim();
			}
		}

		// finaly hacky fixups to the info, all based on observation
		if (rokuInfo != null) {
			finalTweaks(rokuInfo);
			log.info("TVDB Parser found: " + rokuInfo.toString());
		}
		
		return(rokuInfo);
	}

	// +-------------+
	// | finalTweaks |
	// +-------------+

	private void finalTweaks(RokuSearchInfo info) {
		Matcher m = REGEX_YEARSUFFIX.matcher(info.Search);
		if (m.matches()) info.Search = m.group(1);
	}
	
	// +-----------------+
	// | findRokuChannel |
	// +-----------------+

	public String findRokuChannel(String networkId) {

		return(cfg.RokuChannelMap.containsKey(networkId) ?
			   cfg.RokuChannelMap.get(networkId) : null);
	}

	// +------------+
	// | Short URLs |
	// +------------+

	public ShortUrlInfo getShortUrlInfo(String url) throws Exception {
		return(shortUrlProxy.getItem(url));
	}

	public static class ShortUrlCachingProxy
		extends CachingProxy<String, ShortUrlInfo>
				
	{
		public ShortUrlCachingProxy(CachingProxy.Config cfg, DB db, Api api)
			throws Exception {
			
			super(cfg);
			this.db = db;
			this.api = api;
		}

		public ShortUrlInfo liveFetch(String url) throws Exception
		{ return(api.getShortUrl(url)); }
		
		public ShortUrlInfo cacheFetch(String url) throws Exception
		{ return(db.getShortUrl(url)); }
		
		public void cacheStore(String url, ShortUrlInfo info) throws Exception
		{ db.putShortUrl(info); }
		
		public Instant cacheTime(String url, ShortUrlInfo info) throws Exception 
		{ return(info.Created); }

		private Api api;
		private DB db;
	}

	// +---------+
	// | Episode |
	// +---------+

	public Episode getEpisode(int id) throws Exception {
		return(episodeProxy.getItem(id));
	}

	public static class EpisodeCachingProxy extends CachingProxy<Integer,Episode>
	{
		public EpisodeCachingProxy(CachingProxy.Config cfg, DB db, Api api)
			throws Exception {
			
			super(cfg);
			this.db = db;
			this.api = api;
		}

		public Episode liveFetch(Integer id) throws Exception
		{ return(api.getEpisode(id, false)); }
		
		public Episode cacheFetch(Integer id) throws Exception
		{ return(db.getEpisode(id)); }
		
		public void cacheStore(Integer id, Episode e) throws Exception
		{ db.putEpisode(e); }
		
		public Instant cacheTime(Integer id, Episode e) throws Exception 
		{ return(e.Created); }

		private Api api;
		private DB db;
	}

	// +--------+
	// | Series |
	// +--------+

	public Series getSeries(int id) throws Exception {
		return(seriesProxy.getItem(id));
	}

	public static class SeriesCachingProxy extends CachingProxy<Integer,Series>
	{
		public SeriesCachingProxy(CachingProxy.Config cfg, DB db, Api api)
			throws Exception {
			
			super(cfg);
			this.db = db;
			this.api = api;
		}

		public Series liveFetch(Integer id) throws Exception
		{ return(api.getSeries(id)); }
		
		public Series cacheFetch(Integer id) throws Exception
		{ return(db.getSeries(id)); }
		
		public void cacheStore(Integer id, Series s) throws Exception
		{ db.putSeries(s); }
		
		public Instant cacheTime(Integer id, Series s) throws Exception 
		{ return(s.Created); }

		private Api api;
		private DB db;
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String args[]) throws Exception {
		// nyi
	}

	// +-------------------+
	// | Private Constants |
	// +-------------------+

	private static String TVTIME_URL_MARKER = "https://tvtime.com";
	private static String ON_TVTIME_MARKER = " on tv time";

	private static Pattern REGEX_YEARSUFFIX =
		Pattern.compile("^(.+)\\s+\\([12][0123456789]{3}\\)$");

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private Config cfg;
	private DB db;
	private Api api;
	
	private EpisodeCachingProxy episodeProxy;
	private SeriesCachingProxy seriesProxy;
	private ShortUrlCachingProxy shortUrlProxy;

	private Map<String,String> rokuChannelMap;



	private final static Logger log = Logger.getLogger(Lookup.class.getName());
}

