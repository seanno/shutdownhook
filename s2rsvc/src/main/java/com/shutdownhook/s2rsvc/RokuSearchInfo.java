/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;

public class RokuSearchInfo
{
	public String Search;
	public String Season;
	public String Number;
	public String Channel;
	public String ContentId;
	public String MediaType;

	@Override
	public String toString() {
		return(String.format("%s|%s|%s|%s|%s|%s",
				 Search, Season, Number, Channel, ContentId, MediaType));
	}

	public interface Parser extends Closeable {
		// return an info if you were able to parse, otherwise null
		public RokuSearchInfo parse(String input, UserChannelSet channels) throws Exception;
	}

	public interface Refiner extends Closeable {
		// do your best to improve things in place
		public void refine(RokuSearchInfo info, UserChannelSet channels) throws Exception;
	}
}

