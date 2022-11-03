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
	
	// +-------+
	// | fixup |
	// +-------+

	public void fixup(ParsedSearch inputSearch) {

		try {
			ParsedSearch fixupSrch = getFixups().get(inputSearch.Search.toLowerCase());
			if (fixupSrch == null) return;

			log.info("FixupParser tweaking input: " + inputSearch);
			if (fixupSrch.Search != null) inputSearch.Search = fixupSrch.Search;
			if (fixupSrch.Season != null) inputSearch.Season = fixupSrch.Season;
			if (fixupSrch.Number != null) inputSearch.Number = fixupSrch.Number;
			if (fixupSrch.Channel != null) inputSearch.Channel = fixupSrch.Channel;
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "FixupParser.fixup", true));
		}
	}

	// +--------------+
	// | ensureFixups |
	// +--------------+

	public synchronized Map<String,ParsedSearch> getFixups() throws IOException {

		if (fixups != null && Instant.now().isBefore(fixupsExpire)) {
			return(fixups);
		}

		String fixupContents = Easy.stringFromSmartyPath(fixupPath);
		fixups = new HashMap<String,ParsedSearch>();
		
		for (String line : fixupContents.split("\n")) {
			
			String[] fields = line.trim().split("\t");
			if (fields.length < 5) {
				if (fields.length != 0) log.warning("Skipping malformed line: " + line);
				continue;
			}

			ParsedSearch fixedSrch = new ParsedSearch();
			fixedSrch.Search = noopString(fields[1]);
			fixedSrch.Season = noopInt(fields[2]);
			fixedSrch.Number = noopInt(fields[3]);
			fixedSrch.Channel = noopString(fields[4]);

			fixups.put(fields[0].trim().toLowerCase(), fixedSrch);
		}

		fixupsExpire = Instant.now().plusSeconds(refreshSeconds);
		return(fixups);
	}

	private String noopString(String input) {
		String trimmed = input.trim();
		return((trimmed.isEmpty() || trimmed.equals("-")) ? null : trimmed);
	}

	private Integer noopInt(String input) {
		String trimmed = input.trim();
		return((trimmed.isEmpty() || trimmed.equals("-")) ? null : Integer.parseInt(trimmed));
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private String fixupPath;
	private int refreshSeconds;

	private Map<String,ParsedSearch> fixups;
	private Instant fixupsExpire;
	
	private final static Logger log = Logger.getLogger(FixupParser.class.getName());
}
