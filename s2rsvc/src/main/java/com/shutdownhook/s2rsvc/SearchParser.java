/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.s2rsvc.FixupParser;
import com.shutdownhook.s2rsvc.SyntaxParser;
import com.shutdownhook.s2rsvc.WikiShows;
import com.shutdownhook.s2rsvc.tvdb.Lookup;
	
public class SearchParser implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WikiShows.Config Wiki;
		public Lookup.Config TVDB;

		public String FixupPath = "@defaultFixups.tsv";
		public Integer FixupRefreshSeconds = 60 * 60 * 24; // 24 hrs
	}

	public SearchParser(Config cfg) throws Exception {
		this.cfg = cfg;
		this.wikiShows = new WikiShows(cfg.Wiki);
		this.tvdbLookup = new Lookup(cfg.TVDB);
		this.syntaxParser = new SyntaxParser();
		this.fixupParser = new FixupParser(cfg.FixupPath, cfg.FixupRefreshSeconds);
	}

	public void close() {
		wikiShows.close();
		tvdbLookup.close();
	}

	// +--------------+
	// | ParsedSearch |
	// +--------------+
	
	public static class ParsedSearch
	{
		public String Search;
		public Integer Season;
		public Integer Number;
		public String Channel;

		@Override
		public String toString() {
			return(String.format("%s|%d|%d|%s", Search, Season, Number, Channel));
		}
	}

	// +-------+
	// | parse |
	// +-------+

	public ParsedSearch parse(String input) {
		
		ParsedSearch srch = null;
		String trimmed = input.trim();

		try {
			// If we see a TVDB short url, just use that. 
			srch = tvdbLookup.parseSearch(trimmed);
			if (srch != null) {
				log.info("<PARSED> via tvdb: " + srch.toString());
			}
			else {
				// do a precheck to parse out structure if we see it
				ParsedSearch preSrch = syntaxParser.parseSearch(trimmed);

				if (preSrch == null) {
					// just match original input against wiki
					srch = wikiShows.parseSearch(trimmed);
					if (srch != null) log.info("<PARSED> via wiki only: " + srch.toString());
				}
				else {
					// found some syntax, try it against wiki
					srch = wikiShows.parseSearch(preSrch.Search);

					if (srch == null) {
						// nope just syntax
						srch = preSrch;
						log.info("<PARSED> via syntax only: " + srch.toString());
					}
					else {
						// yep combine them
						srch.Season = preSrch.Season;
						srch.Number = preSrch.Number;
						log.info("<PARSED> via wiki + syntax: " + srch.toString());
					}
				}
			}
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "SearchParser.parse", true));
			srch = null;
		}

		if (srch == null) {
			srch = new ParsedSearch();
			srch.Search = trimmed;
		}

		// last chance manual tweaks!
		fixupParser.fixup(srch);
		
		return(srch);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private WikiShows wikiShows;
	private Lookup tvdbLookup;
	private SyntaxParser syntaxParser;
	private FixupParser fixupParser;

	private final static Logger log = Logger.getLogger(SearchParser.class.getName());
}

