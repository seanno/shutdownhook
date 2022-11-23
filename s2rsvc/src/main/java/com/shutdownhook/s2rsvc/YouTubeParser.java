/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.util.logging.Logger;

public class YouTubeParser implements RokuSearchInfo.Parser
{
	// +-----------------------+
	// | RokuSearchInfo.Parser |
	// +-----------------------+

	public void close() {
		// nut-n-honey
	}

	public RokuSearchInfo parse(String input, UserChannelSet channels) throws Exception {

		RokuSearchInfo info = tryMarker(input, URL_MARKER_1);
		if (info == null) info = tryMarker(input, URL_MARKER_2);

		return(info);
	}

	private RokuSearchInfo tryMarker(String input, String marker) {

		int ich = input.toLowerCase().indexOf(marker);
		if (ich == -1 ) return(null);

		ich += marker.length();

		int ichEnd = ich + 1;
		int cch = input.length();

		while (ichEnd < cch) {
			if (ichEnd == '/' || ichEnd == '?' || ichEnd == '&') break;
			++ichEnd;
		}
		
		RokuSearchInfo info = new RokuSearchInfo();
		info.Search = input;
		info.Channel = CHANNEL_ID;
		info.ContentId = input.substring(ich, ichEnd);
		info.MediaType = MEDIA_TYPE;
		
		log.info("YouTube parser: " + info.toString());
		return(info);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private final static String URL_MARKER_1 = "https://youtu.be/";
	private final static String URL_MARKER_2 = "youtube.com/watch?v=";
	
	private final static String CHANNEL_ID = "837";
	private final static String MEDIA_TYPE = "live";

	private final static Logger log = Logger.getLogger(YouTubeParser.class.getName());
}
