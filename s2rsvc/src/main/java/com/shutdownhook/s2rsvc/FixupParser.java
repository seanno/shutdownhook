/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.s2rsvc.SearchParser.ParsedSearch;

public class FixupParser
{
	public FixupParser(String fixupPath, int refreshSeconds) {
		this.fixupPath = fixupPath;
		this.refreshSeconds = refreshSeconds;
	}

	// +-------------------+
	// | Fixup File Format |
	// +-------------------+
	
	// TSV format is one line per fixup, tab separated. First column
	// is search text to match, next four are directives for each
	// field (Search + Season + Number + Channel).
	//
	// A "x" in a column means to delete; "-" means to leave whatever
	// is there alone; anything else means replace with the contents
	// of the column

	private static String LEAVE_DIRECTIVE = "-";
	private static String DELETE_DIRECTIVE = "x";
		
	private static int INPUT_IDX = 0;
	private static int SEARCH_IDX = 1;
	private static int SEASON_IDX = 2;
	private static int NUMBER_IDX = 3;
	private static int CHANNEL_IDX = 4;

	// +-------+
	// | fixup |
	// +-------+

	public boolean fixup(ParsedSearch inputSearch) {

		boolean ret = false;
		
		try {
			String[] fields = getFixups(inputSearch.Search.toLowerCase());
			if (fields == null) return(false);

			log.info("FixupParser tweaking input: " + inputSearch);
			ret = true;

			inputSearch.Search = fixupField(fields[SEARCH_IDX], inputSearch.Search);
			inputSearch.Season = fixupField(fields[SEASON_IDX], inputSearch.Season);
			inputSearch.Number = fixupField(fields[NUMBER_IDX], inputSearch.Number);
			inputSearch.Channel = fixupField(fields[CHANNEL_IDX], inputSearch.Channel);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "FixupParser.fixup", true));
		}

		return(ret);
	}

	private String fixupField(String directive, String current) {
		if (directive.equals(LEAVE_DIRECTIVE)) return(current);
		if (directive.equals(DELETE_DIRECTIVE)) return(null);
		return(directive);
	}

	// +--------------+
	// | ensureFixups |
	// +--------------+

	public synchronized String[] getFixups(String input) throws IOException {

		if (fixups != null && Instant.now().isBefore(fixupsExpire)) {
			return(fixups.get(input));
		}

		String fixupContents = Easy.stringFromSmartyPath(fixupPath);
		fixups = new HashMap<String,String[]>();
		
		for (String line : fixupContents.split("\n")) {
			
			String[] fields = line.split("\t");
			if (fields.length < 5) {
				if (fields.length != 0) log.warning("Skipping malformed line: " + line);
				continue;
			}

			for (int i = 0; i < fields.length; ++i)
				fields[i] = fields[i].trim();

			fixups.put(fields[0].toLowerCase(), fields);
		}

		fixupsExpire = Instant.now().plusSeconds(refreshSeconds);
		return(fixups.get(input));
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private String fixupPath;
	private int refreshSeconds;

	private Map<String,String[]> fixups;
	private Instant fixupsExpire;
	
	private final static Logger log = Logger.getLogger(FixupParser.class.getName());
}
