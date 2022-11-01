/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

public class ParsedSearch
{
	public String Search;
	public Integer Season;
	public Integer Number;
	public String Channel;

	@Override
	public String toString() {
		return(String.format("Search=%s; Season=%d; Number=%d; Channel=%s",
							 Search, Season, Number, Channel));
	}
}

