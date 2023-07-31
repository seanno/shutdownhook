/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc.tvdb;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.SqlStore;

import com.shutdownhook.s2rsvc.tvdb.Model.Episode;
import com.shutdownhook.s2rsvc.tvdb.Model.Series;
import com.shutdownhook.s2rsvc.tvdb.Model.ShortUrlInfo;

public class DB extends SqlStore
{
	public DB(SqlStore.Config cfg) throws Exception {
		super(cfg);
		ensureTables();
	}

	// +---------+
	// | Queries |
	// +---------+

	public Episode getEpisode(int id) throws Exception {

		SqlStore.Return<Episode> e = new SqlStore.Return<Episode>();
		
		query(QUERY_EPISODE, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setInt(1, id);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception(">1 found for " + id);
				e.Value = episodeFromRow(rs);
			}
		});

		return(e.Value);
	}

	public Series getSeries(int id) throws Exception {

		SqlStore.Return<Series> s = new SqlStore.Return<Series>();
		
		query(QUERY_SERIES, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setInt(1, id);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception(">1 found for " + id);
				s.Value = seriesFromRow(rs);
			}
		});

		return(s.Value);
	}

	public ShortUrlInfo getShortUrl(String url) throws Exception {

		SqlStore.Return<ShortUrlInfo> info = new SqlStore.Return<ShortUrlInfo>();
		
		query(QUERY_SHORT_URL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, url);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception(">1 found for " + url);
				info.Value = shortUrlFromRow(rs);
			}
		});

		return(info.Value);
	}

	// +---------+
	// | Updates |
	// +---------+

	public boolean putEpisode(Episode e) throws Exception {

		String stmt = (getEpisode(e.Id) == null ?
					   INSERT_EPISODE : UPDATE_EPISODE);
		
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(stmt, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {

				stmt.setInt(1, e.SeriesId);

				if (e.Season == null) stmt.setNull(2, Types.INTEGER);
				else stmt.setInt(2, e.Season);
										
				if (e.Number == null) stmt.setNull(3, Types.INTEGER);
				else stmt.setInt(3, e.Number);

				stmt.setLong(4, Instant.now().getEpochSecond());
				stmt.setInt(5, e.Id);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value == true);
	}

	public void deleteEpisode(int id) throws Exception {

		update(DELETE_EPISODE, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {
				
				stmt.setInt(1, id);
			}
		});
	}

	public boolean putSeries(Series s) throws Exception {

		String stmt = (getSeries(s.Id) == null ?
					   INSERT_SERIES : UPDATE_SERIES);
		
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(stmt, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {

				if (s.NetworkId == null) stmt.setNull(1, Types.VARCHAR);
				else stmt.setString(1, s.NetworkId);
										
				if (s.Name == null) stmt.setNull(2, Types.VARCHAR);
				else stmt.setString(2, s.Name);

				stmt.setLong(3, Instant.now().getEpochSecond());
				stmt.setInt(4, s.Id);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value == true);
	}

	public void deleteSeries(int id) throws Exception {

		update(DELETE_SERIES, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {
				
				stmt.setInt(1, id);
			}
		});
	}

	public boolean putShortUrl(ShortUrlInfo info) throws Exception {

		String stmt = (getShortUrl(info.Url) == null ?
					   INSERT_SHORT_URL : UPDATE_SHORT_URL);
		
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(stmt, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {

				if (info.EpisodeId == null) stmt.setNull(1, Types.INTEGER);
				else stmt.setInt(1, info.EpisodeId);
										
				if (info.SeriesId == null) stmt.setNull(2, Types.INTEGER);
				else stmt.setInt(2, info.SeriesId);

				if (info.MovieId == null) stmt.setNull(3, Types.VARCHAR);
				else stmt.setString(3, info.MovieId);

				stmt.setLong(4, Instant.now().getEpochSecond());
				stmt.setString(5, info.Url);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value == true);
	}

	public void deleteShortUrl(String url) throws Exception {

		update(DELETE_SHORT_URL, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter)
				throws Exception {
				
				stmt.setString(1, url);
			}
		});
	}

	// +-----+
	// | DDL |
	// +-----+

	private void ensureTables() throws Exception {
		ensureTable("series", CREATE_SERIES_TABLE);
		ensureTable("episodes", CREATE_EPISODES_TABLE);
		ensureTable("short_urls", CREATE_SHORT_URLS_TABLE);
	}

	private void ensureTable(String tableName, String ddl) throws Exception {
		if (!tableExists(tableName)) {
			this.update(ddl);
		}
	}

	private boolean tableExists(String tableName) throws Exception {

		boolean exists = false;
		
		try {
			String sql = "select 1 from " + tableName + " limit 1";
			query(sql, new SqlStore.QueryHandler() {
				public void row(ResultSet rs, int irow) throws Exception { }
			});
			
			exists = true;
		}
		catch (Exception e) {
			// nothing
		}

		return(exists);
	}

	// +---------+
	// | Objects |
	// +---------+

	private static Series seriesFromRow(ResultSet rs) throws SQLException {
		Series s = new Series();
		s.Id = rs.getInt("s_id"); 
		s.Created = Instant.ofEpochSecond(rs.getInt("s_epoch_created"));
		s.NetworkId = rs.getString("s_network_id");
		s.Name = rs.getString("s_name");
		return(s);
	}
	
	private static Episode episodeFromRow(ResultSet rs) throws SQLException {
		Episode e = new Episode();
		e.Id = rs.getInt("e_id"); 
		e.Created = Instant.ofEpochSecond(rs.getInt("e_epoch_created"));
		e.SeriesId = getNullableInt(rs, "e_series_id");
		e.Season = getNullableInt(rs, "e_season");
		e.Number = getNullableInt(rs, "e_number");
		e.Series = seriesFromRow(rs);
		return(e);
	}

	private static ShortUrlInfo shortUrlFromRow(ResultSet rs) throws SQLException {
		ShortUrlInfo info = new ShortUrlInfo();
		info.Url = rs.getString("su_url"); 
		info.Created = Instant.ofEpochSecond(rs.getInt("su_epoch_created"));
		info.EpisodeId = getNullableInt(rs, "su_episode_id");
		info.SeriesId = getNullableInt(rs, "su_series_id");
		info.MovieId = rs.getString("su_movie_id");
		return(info);
	}
	
	// +-----+
	// | SQL |
	// +-----+

	private static String INSERT_EPISODE =
		"insert into episodes " +
		"(series_id, season, number, epoch_created, id) " +
		"values (?,?,?,?,?) ";

	private static String UPDATE_EPISODE =
		"update episodes set " +
		"series_id = ?, season = ?, number = ?, epoch_created = ? " +
		"where id = ?";

	private static String DELETE_EPISODE =
		"delete from episodes where id = ? ";
		  
	private static String INSERT_SERIES =
		"insert into series " +
		"(network_id, name, epoch_created, id) " +
		"values (?,?,?,?) ";

	private static String UPDATE_SERIES =
		"update series set " +
		"network_id = ?, name = ?, epoch_created = ? " +
		"where id = ?";

	private static String DELETE_SERIES =
		"delete from series where id = ? ";

	private static String INSERT_SHORT_URL =
		"insert into short_urls " +
		"(episode_id, series_id, movie_id, epoch_created, url) " +
		"values (?,?,?,?,?) ";

	private static String UPDATE_SHORT_URL =
		"update short_urls set " +
		"episode_id = ?, series_id = ?, movie_id = ?, epoch_created = ? " +
		"where url = ?";

	private static String DELETE_SHORT_URL =
		"delete from short_urls where url = ? ";

	private static String QUERY_EPISODE =
		"select " +
		"  e.id e_id, " +
		"  e.season e_season, " +
		"  e.number e_number, " +
		"  e.series_id e_series_id, " + 
		"  e.epoch_created e_epoch_created, " +
		"  s.id s_id, " +
		"  s.network_id s_network_id, " +
		"  s.name s_name, " +
		"  s.epoch_created s_epoch_created " +
		"from " +
		"  episodes e " +
		"left outer join " +
		"  series s " +
		"  on e.series_id = s.id " +
		"where " +
		"  e.id = ? ";
		
	private static String QUERY_SERIES =
		"select " +
		"  s.id s_id, " +
		"  s.network_id s_network_id, " +
		"  s.name s_name, " +
		"  s.epoch_created s_epoch_created " +
		"from " +
		"  series s " +
		"where " +
		"  s.id = ? ";

	private static String QUERY_SHORT_URL =
		"select " +
		"  su.url su_url, " +
		"  su.episode_id su_episode_id, " +
		"  su.series_id su_series_id, " +
		"  su.movie_id su_movie_id, " +
		"  su.epoch_created su_epoch_created " +
		"from " +
		"  short_urls su " +
		"where " +
		"  su.url = ? ";

	private static String CREATE_EPISODES_TABLE =
		"create table episodes (" +
		"  id integer not null, " +
		"  epoch_created integer not null, " +
		"  series_id integer null, " +
		"  season integer null, " +
		"  number integer null, " +
		"  primary key (id) " +
		") ";
		
	private static String CREATE_SERIES_TABLE =
		"create table series (" +
		"  id integer not null, " +
		"  epoch_created integer not null, " +
		"  network_id varchar(64) null, " +
		"  name varchar(128) null, " +
		"  primary key (id) " +
		") ";

	private static String CREATE_SHORT_URLS_TABLE =
		"create table short_urls (" +
		"  url varchar(1024) not null, " +
		"  epoch_created integer not null, " +
		"  episode_id integer null, " +
		"  series_id integer null, " +
		"  movie_id varchar(255) null, " +
		"  primary key (url) " +
		") ";
}

