/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.tvdb;

import java.time.Instant;

public class Model
{
	// +--------+
	// | Series |
	// +--------+
	
	public static class Series
	{
		public Integer Id;
		public Instant Created;
		public String NetworkId;
		public String Name;

		@Override
		public String toString() {
			return(String.format("Id=%d; NetworkId=%s; Name=%s; Created=%s",
								 Id, NetworkId, Name, Created));

		}
	}

	// +---------+
	// | Episode |
	// +---------+

	public static class Episode
	{
		public Integer Id;
		public Instant Created;
		public Integer Season;
		public Integer Number;
		public Integer SeriesId;
		public Series Series;

		@Override
		public String toString() {
			return(String.format(
			    "Id=%d; Season=%d; Number=%d; SeriesId=%d; Created=%s\nSeries: %s",
				Id, Season, Number, SeriesId, Created, Series));

		}
	}

	// +--------------+
	// | ShortUrlInfo |
	// +--------------+
	
	public static class ShortUrlInfo
	{
		public String Url;
		public Instant Created;
		public Integer EpisodeId;
		public Integer SeriesId;
		public String MovieId;

		@Override
		public String toString() {
			return(String.format(
			    "Url=%s; Episode=%d; Series=%d; Movie=%s; Created=%s",
				Url, EpisodeId, SeriesId, MovieId, Created));
		}
	}
}

