/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxParser implements RokuSearchInfo.Parser
{
	// +-----------------------+
	// | RokuSearchInfo.Parser |
	// +-----------------------+

	public void close() {
		// nut-n-honey
	}

	public RokuSearchInfo parse(String input, UserChannelSet channels) throws Exception {

		// ends with "season x"
		Matcher m = REGEX_SEASON.matcher(input);
		if (m.matches()) {
			RokuSearchInfo info = new RokuSearchInfo();
			info.Search = m.group(1);
			info.Season = m.group(2);
			log.info("SyntaxParser found season match: " + info.toString());
			return(info);
		}

		// ends with "SxEy"
		m = REGEX_SEASON_EPISODE.matcher(input);
		if (m.matches()) {
			RokuSearchInfo info = new RokuSearchInfo();
			info.Search = m.group(1);
			info.Season = m.group(2);
			info.Number = m.group(3);
			log.info("SyntaxParser found SxEy match: " + info.toString());
			return(info);
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

	private final static Logger log = Logger.getLogger(SyntaxParser.class.getName());
}
