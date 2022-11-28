/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;
import java.util.Set;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.s2rsvc.tvdb.Lookup;
	
public class SearchController implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WikiRefiner.Config Wiki = new WikiRefiner.Config();
		public Lookup.Config TVDB = new Lookup.Config();
		public FixupRefiner.Config Fixups = new FixupRefiner.Config();
		public RokuSearchRefiner.Config Roku = new RokuSearchRefiner.Config();
	}

	public SearchController(Config cfg) throws Exception {
		this.cfg = cfg;

		this.tvdbParser = new Lookup(cfg.TVDB);
		this.youtubeParser = new YouTubeParser();
		this.syntaxParser = new SyntaxParser();

		this.wikiRefiner = new WikiRefiner(cfg.Wiki);
		this.rokuRefiner = new RokuSearchRefiner(cfg.Roku);
		this.fixupRefiner = new FixupRefiner(cfg.Fixups);
	}

	public void close() {
		safeClose(fixupRefiner);
		safeClose(rokuRefiner);
		safeClose(wikiRefiner);
		safeClose(syntaxParser);
		safeClose(youtubeParser);
		safeClose(tvdbParser);
	}

	private void safeClose(Closeable c) {
		try { c.close(); }
		catch (Exception e) { log.warning(Easy.exMsg(e, "safeClose", true)); }
	}

	// +-------+
	// | parse |
	// +-------+

	public RokuSearchInfo parse(String input, String channelsCSV) {
		return(parse(input, UserChannelSet.fromCSV(channelsCSV)));
	}
	
	public RokuSearchInfo parse(String input, UserChannelSet channels) {

		// 1. PARSE
		
		String trimmed = input.trim();
		RokuSearchInfo info = null;
		
		try {
			info = tvdbParser.parse(input, channels);
			if (info == null) info = youtubeParser.parse(input, channels);
			if (info == null) info = syntaxParser.parse(input, channels);
		}
		catch (Exception eParse) {
			log.warning(Easy.exMsg(eParse, "parsers", true));
			info = null;
		}
		
		if (info == null) {
			info = new RokuSearchInfo();
			info.Search = trimmed;
			log.info("Default RokuSearchInfo: " + info.toString());
		}

		// 2. REFINE

		tryRefine(info, channels, rokuRefiner, "rokuRefiner");
		tryRefine(info, channels, wikiRefiner, "wikiRefiner");
		tryRefine(info, channels, fixupRefiner, "fixupRefiner");

		// 3. RETURN
		
		log.info(String.format("FINAL [%s] -> %s", trimmed, info));
		return(info);
	}

	private void tryRefine(RokuSearchInfo info,
						   UserChannelSet channels,
						   RokuSearchInfo.Refiner refiner,
						   String tag) {

		try { refiner.refine(info, channels); }
		catch (Exception e) { log.warning(Easy.exMsg(e, tag, true)); }
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;

	private RokuSearchInfo.Parser tvdbParser;
	private RokuSearchInfo.Parser youtubeParser;
	private RokuSearchInfo.Parser syntaxParser;

	private RokuSearchInfo.Refiner wikiRefiner;
	private RokuSearchInfo.Refiner rokuRefiner;
	private RokuSearchInfo.Refiner fixupRefiner;

	private final static Logger log =
		Logger.getLogger(SearchController.class.getName());
}

