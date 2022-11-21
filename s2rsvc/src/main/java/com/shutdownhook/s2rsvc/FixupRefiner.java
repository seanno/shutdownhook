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

public class FixupRefiner implements RokuSearchInfo.Refiner
{
	public static class Config
	{
		public String TsvPath = "@defaultFixups.tsv";
		public Integer RefreshSeconds = 60 * 60 * 24; // 24 hrs
	}

	public FixupRefiner(Config cfg) {
		this.cfg = cfg;
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

	// +------------------------+
	// | RokuSearchInfo.Refiner |
	// +------------------------+
	
	public void close() {
		// nut-n-honey
	}

	public void refine(RokuSearchInfo info) throws Exception {

		try {
			String[] fields = getFixups(info.Search.toLowerCase());
			if (fields == null) return;

			info.Search = fixup(fields[SEARCH_IDX], info.Search);
			info.Season = fixup(fields[SEASON_IDX], info.Season);
			info.Number = fixup(fields[NUMBER_IDX], info.Number);
			info.Channel = fixup(fields[CHANNEL_IDX], info.Channel);

			log.info("FixupRefiner updated info: " + info.toString());
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "FixupRefiner.refine", true));
		}
	}

	private String fixup(String directive, String current) {
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

		String fixupContents = Easy.stringFromSmartyPath(cfg.TsvPath);
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

		fixupsExpire = Instant.now().plusSeconds(cfg.RefreshSeconds);
		return(fixups.get(input));
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;

	private Map<String,String[]> fixups;
	private Instant fixupsExpire;
	
	private final static Logger log =
		Logger.getLogger(FixupRefiner.class.getName());
}
