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
		public String Season;
		public String Number;
		public String Channel;

		@Override
		public String toString() {
			return(String.format("%s|%s|%s|%s", Search, Season, Number, Channel));
		}
	}

	// +-------+
	// | parse |
	// +-------+

	public ParsedSearch parse(String input) {

		String method = "";
		ParsedSearch srch = null;
		String trimmed = input.trim();

		try {
			// If we see a TVDB short url, just use that. 
			srch = tvdbLookup.parseSearch(trimmed);
			if (srch != null) {
				method = "tvdb";

				if (srch.Channel == null) {
					// see if wiki has the channel
					ParsedSearch wikiSrch = wikiShows.parseSearch(srch.Search);
					if (wikiSrch != null && wikiSrch.Channel != null) {
						method += " + wiki";
						srch.Channel = wikiSrch.Channel;
					}
				}
			}
			else {
				// do a precheck to parse out structure if we see it
				ParsedSearch preSrch = syntaxParser.parseSearch(trimmed);

				if (preSrch == null) {
					// just match original input against wiki
					srch = wikiShows.parseSearch(trimmed);
					if (srch != null) method = "wiki only";
				}
				else {
					// found some syntax, try it against wiki
					srch = wikiShows.parseSearch(preSrch.Search);

					if (srch == null) {
						// nope just syntax
						srch = preSrch;
						method = "syntax only";
					}
					else {
						// yep combine them
						srch.Season = preSrch.Season;
						srch.Number = preSrch.Number;
						method = "wiki + syntax";
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
			method = "default";
		}

		// last chance manual tweaks!
		if (fixupParser.fixup(srch)) method += " (fixup)";

		log.info(String.format("<PARSED> %s [%s] to %s", method, input, srch));
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

