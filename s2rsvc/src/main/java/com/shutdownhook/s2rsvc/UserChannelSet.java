/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.util.HashSet;
	
public class UserChannelSet extends HashSet<String>
{
	public boolean ok(String channel) {
		// empty channel set means "i dunno" so assume ok
		if (this.size() == 0) return(true);
		// if you don't know what you're asking about I guess it's ok?
		if (channel == null) return(true);
		// actually look
		return(this.contains(channel));
	}

	public static UserChannelSet fromCSV(String input) {
			
		UserChannelSet channels = new UserChannelSet();
		if (input == null || input.trim().isEmpty()) return(channels);

		for (String channel : input.split(",")) {
			String trimmed = channel.trim();
			if (!trimmed.isEmpty()) channels.add(trimmed);
		}

		return(channels);
	}
}
