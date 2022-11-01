/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.syntax;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shutdownhook.s2rsvc.SearchParser.ParsedSearch;

public class SyntaxParser
{
	// +-------------+
	// | parseSearch |
	// +-------------+

	public ParsedSearch parseSearch(String input) throws Exception {

		Matcher m = REGEX_SEASON.matcher(input);
		if (m.matches()) {
			ParsedSearch srch = new ParsedSearch();
			srch.Search = m.group(1);
			srch.Season = Integer.parseInt(m.group(2));
			return(srch);
		}

		m = REGEX_SEASON_EPISODE.matcher(input);
		if (m.matches()) {
			ParsedSearch srch = new ParsedSearch();
			srch.Search = m.group(1);
			srch.Season = Integer.parseInt(m.group(2));
			srch.Number = Integer.parseInt(m.group(3));
			return(srch);
		}

		int ich = input.toLowerCase().indexOf(ON_TV_TIME_MARKER);
		if (ich != -1) {
			ParsedSearch srch = new ParsedSearch();
			srch.Search = input.substring(0, ich);
			return(srch);
		}

		return(null);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

    private static Pattern REGEX_SEASON =
		Pattern.compile("^(.+)\\s+[sS][eE][aA][sS][oO][nN]\\s+(\\d+)$");
	
    private static Pattern REGEX_SEASON_EPISODE =
		Pattern.compile("^(.+)\\s+[sS](\\d+)[eE](\\d+)$");

	private static String ON_TV_TIME_MARKER =
		" on tv time";
	
	private final static Logger log = Logger.getLogger(SyntaxParser.class.getName());
}
