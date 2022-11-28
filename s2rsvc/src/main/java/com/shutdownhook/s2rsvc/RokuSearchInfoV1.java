/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;

public class RokuSearchInfoV1
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

}

