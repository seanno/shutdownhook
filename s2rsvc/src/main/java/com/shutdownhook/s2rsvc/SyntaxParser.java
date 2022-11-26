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

		String cleanInput = input;

		// clean up weird chrome share format; pick out just the selected text
		Matcher m = REGEX_CHROME_SHARE.matcher(cleanInput);
		if (m.matches()) {
			cleanInput = m.group(1);
		}
		
		// ends with "season x"
		m = REGEX_SEASON.matcher(cleanInput);
		if (m.matches()) {
			RokuSearchInfo info = new RokuSearchInfo();
			info.Search = m.group(1);
			info.Season = m.group(2);
			log.info("SyntaxParser found season match: " + info.toString());
			return(info);
		}

		// ends with "SxEy"
		m = REGEX_SEASON_EPISODE.matcher(cleanInput);
		if (m.matches()) {
			RokuSearchInfo info = new RokuSearchInfo();
			info.Search = m.group(1);
			info.Season = m.group(2);
			info.Number = m.group(3);
			log.info("SyntaxParser found SxEy match: " + info.toString());
			return(info);
		}

		if (!input.equals(cleanInput)) {
			RokuSearchInfo info = new RokuSearchInfo();
			info.Search = cleanInput;
			log.info("SyntaxParser cleaned input: " + info.toString());
			return(info);
		}
		
		return(null);
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		SyntaxParser parser = null;

		try {
			parser = new SyntaxParser();

			String input = args[0];
			String channelsParam = (args.length >= 2 ? args[1] : null);
			UserChannelSet channels = UserChannelSet.fromCSV(channelsParam);

			RokuSearchInfo info = parser.parse(input, channels);
			System.out.println(info == null ? "null" : info.toString());
		}
		finally {
			
			if (parser != null) parser.close();
		}
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+


    private static Pattern REGEX_SEASON =
		Pattern.compile("^(.+)\\s+[sS][eE][aA][sS][oO][nN]\\s+(\\d+)$");
	
    private static Pattern REGEX_SEASON_EPISODE =
		Pattern.compile("^(.+)\\s+[sS](\\d+)[eE](\\d+)$");

	private static Pattern REGEX_CHROME_SHARE =
		Pattern.compile("\\\"([^\\\"]+)\\\"\\s+[hH][tT][tT][pP].+");

	private final static Logger log = Logger.getLogger(SyntaxParser.class.getName());
}
