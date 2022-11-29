/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.util.logging.Logger;

public class UrlParser implements RokuSearchInfo.Parser
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Marker
	{
		public String Marker;
		public String Channel;
		public String MediaType = "live";
		public String MarkerEnd = null;
	}
	
	public static class Config
	{
		public Marker[] Markers;
		public Integer TruncationLength = 60;
		public String TruncationSuffix = "...";
	}

	public UrlParser(Config cfg) {
		this.cfg = cfg;
	}
	
	// +-----------------------+
	// | RokuSearchInfo.Parser |
	// +-----------------------+

	public void close() {
		// nut-n-honey
	}

	public RokuSearchInfo parse(String input, UserChannelSet channels) throws Exception {

		// note these parsers purposefully do not filter by the user
		// channels set ... this parser is identifying a specific channel,
		// so better to prompt the user to install than to pretend we
		// don't know what it is.

		String cleaned = input.trim().toLowerCase();
		
		for (Marker marker : cfg.Markers) {
			RokuSearchInfo info = tryMarker(cleaned, marker);
			
			if (info != null) {
				
				int ichUrl = cleaned.indexOf("http");
				String search = (ichUrl <= 0 ? input : input.substring(0, ichUrl));
				
				if (search.length() > cfg.TruncationLength) {
					search = search.substring(0, cfg.TruncationLength) + cfg.TruncationSuffix;
				}

				info.Search = search.replace("\n", " ");
				return(info);
			}
		}

		return(null);
	}

	// +-----------+
	// | tryMarker |
	// +-----------+
	
	private RokuSearchInfo tryMarker(String input, Marker marker) {

		int ich = input.indexOf(marker.Marker);
		if (ich == -1 ) return(null);

		ich += marker.Marker.length();
		int cch = input.length();
		
		if (marker.MarkerEnd != null) {
			ich = input.indexOf(marker.MarkerEnd, ich);
			if (ich == -1) return(null);
			ich += marker.MarkerEnd.length();
		}

		int ichEnd = ich + 1;
		
		while (ichEnd < cch) {
			char ch = input.charAt(ichEnd);
			if (ch == '/' || ch == '?' || ch == '&') break;
			++ichEnd;
		}

		RokuSearchInfo info = new RokuSearchInfo();
		info.Search = input;

		String contentId = input.substring(ich, ichEnd);
		info.addChannelTarget(marker.Channel, contentId, marker.MediaType, null);
		
		log.info("UrlParser: " + info.toString());
		return(info);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;

	private final static Logger log = Logger.getLogger(UrlParser.class.getName());
}
