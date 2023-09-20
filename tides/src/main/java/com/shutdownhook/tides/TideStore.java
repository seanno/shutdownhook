/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class TideStore extends SqlStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public SqlStore.Config Sql;

		// must be non-null to call saveTide or deleteTide
		public String FilesPath;

		// can be used to render images from a separate image server
		// (e.g., a public Azure container). The prefix must accept
		// a path exactly like the local image store (see fileForTide)
		// starting with YYYY.
		public String ImageUrlPrefix = null;
	}

	public TideStore(Config cfg) throws Exception {
		super(cfg.Sql);
		this.cfg = cfg;
		ensureTables();

		if (cfg.FilesPath != null) new File(cfg.FilesPath).mkdirs();
	}

	// +------+
	// | Tide |
	// +------+

	public static class Tide
	{
		public String TideId;
		public Long EpochSecond;
		public Integer MinuteOfDay;
		public Integer DayOfYear;
		public double TideHeight;
		public Weather.Metrics WeatherMetrics = new Weather.Metrics();
		public File ImageFile;

		public static Tide fromRow(ResultSet rs, Config cfg) throws SQLException {
			
			Tide t = new Tide();
			t.TideId = rs.getString("tide_id");
			t.EpochSecond = rs.getLong("epoch_second");
			t.MinuteOfDay = rs.getInt("minute_of_day");
			t.DayOfYear = rs.getInt("day_of_year");
			t.TideHeight = rs.getDouble("tide_height");

			t.WeatherMetrics.EpochSecond = t.EpochSecond;
			t.WeatherMetrics.UV = rs.getDouble("uv");
			t.WeatherMetrics.Lux = rs.getDouble("lux");
			t.WeatherMetrics.WindMps = rs.getDouble("wind_mps");
			t.WeatherMetrics.TempF = rs.getDouble("temp_f");
			t.WeatherMetrics.PressureMb = rs.getDouble("pressure_mb");
			t.WeatherMetrics.Humidity = rs.getDouble("humidity");

			t.ImageFile = fileForTide(t, cfg);
			
			return(t);
		}

		public String toCSV() {
			Weather.Metrics w = WeatherMetrics;
			return(String.format("%s,%d,%d,%d,%02f,%02f,%02f,%02f,%02f,%02f,%02f,%s",
								 TideId, EpochSecond, MinuteOfDay, DayOfYear, TideHeight,
								 w.UV, w.Lux, w.WindMps, w.TempF, w.PressureMb,
								 w.Humidity, ImageFile.toString()));
		}

		static public String CSV_HEADERS =
			"TideId,EpochSecond,MinuteOfDay,DayOfYear,TideHeight," +
			"UV,Lux,WindMps,TempF,PressureMb,Humidity,ImageFile";
	}

	// +--------------+
	// | queryClosest |
	// +--------------+

	// this query uses thresholds --- if zero results come back, or the
	// "best" match isn't very good, the caller will have to loosen the
	// thresholds and try again. The point is to try to have some options
	// to choose from, but still be reasonably close.

	private final static String CLOSEST_EXCLUDE =
		" and epoch_second != ? ";
		
	private final static String CLOSEST_QUERY =
		"select " +
		"    * " +
		"from " +
		"    tides " +
		"where " +
		"    abs(tide_height - ?) <= ? and " +
		"    case " +
		"        when abs(day_of_year - ?) > (365 / 2) " +
		"        then 365 - abs(day_of_year - ?) " +
		"        else abs(day_of_year - ?) " +
		"    end <= ? and " +
		"    case " +
		"        when abs(minute_of_day - ?) > (1440 / 2) " +
		"        then 1440 - abs(minute_of_day - ?) " +
		"        else abs(minute_of_day - ?) " +
		"    end <= ? " +
		"    [EXCLUDE_CLAUSE] " +
		"order by " +
		"    abs(tide_height - ?) " +
		"limit " +
		"    ? ";

	static public class ClosestTriple
	{
		public ClosestTriple(double height, long minutes, long days) {
			this.Height = height;
			this.Minutes = minutes;
			this.Days = days;
		}

		public double Height;
		public long Minutes;
		public long Days;

		@Override
		public String toString() {
			return(String.format("H=%02f M=%d D=%d", Height, Minutes, Days));
		}
	}

	public List<Tide> queryClosest(ClosestTriple match,
								   ClosestTriple[] progressiveThresholds,
								   int maxResults,
								   Instant excludeTimepoint) throws Exception {

		log.fine("queryClosest matching: " + match.toString());
		
		int i = 1;
		for (ClosestTriple thresholds : progressiveThresholds) {
			log.fine(String.format("queryClosest %d: %s", i++, thresholds));
								   
			List<Tide> tides = queryClosest(match, thresholds, maxResults, excludeTimepoint);
			if (tides.size() > 0) return(tides);
		}

		return(null);
	}

	public List<Tide> queryClosest(ClosestTriple match,
								   ClosestTriple thresholds,
								   int maxResults,
								   Instant excludeTimepoint) throws Exception {

		List<Tide> tides = new ArrayList<Tide>();

		String sql = CLOSEST_QUERY.replace("[EXCLUDE_CLAUSE]",
										   (excludeTimepoint == null ? "" : CLOSEST_EXCLUDE));
		
		query(sql, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {

				int i = 1;
				stmt.setDouble(i++, match.Height);
				stmt.setDouble(i++, thresholds.Height);
				
				stmt.setLong(i++, match.Days);
				stmt.setLong(i++, match.Days);
				stmt.setLong(i++, match.Days);
				stmt.setLong(i++, thresholds.Days);
				
				stmt.setLong(i++, match.Minutes);
				stmt.setLong(i++, match.Minutes);
				stmt.setLong(i++, match.Minutes);
				stmt.setLong(i++, thresholds.Minutes);

				if (excludeTimepoint != null) {
					stmt.setLong(i++, excludeTimepoint.getEpochSecond());
				}
				
				stmt.setDouble(i++, match.Height);

				stmt.setLong(i++, maxResults);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {

				Tide tide = Tide.fromRow(rs, cfg);
				tides.add(tide);
			}
		});

		return(tides);
	}
	
	// +---------+
	// | getTide |
	// +---------+

	private final static String GETTIDE_QUERY =
		"select * from tides where tide_id = ?";

	public Tide getTide(String id) throws Exception {

		SqlStore.Return<Tide> tide = new SqlStore.Return<Tide>();

		query(GETTIDE_QUERY, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, id);
			}

			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception("multiple rows from id query");
				tide.Value = Tide.fromRow(rs, cfg);
			}
		});

		return(tide.Value);
	}

	// +----------------+
	// | getTidesForDay |
	// +----------------+

	private final static String GETTIDESFORDAY_QUERY =
		"select * from tides where epoch_second >= ? and epoch_second < ? ";

	public List<Tide> getTidesForDay(Instant start) throws Exception {

		Instant end = start.plus(1, ChronoUnit.DAYS);
		
		List<Tide> tides = new ArrayList<Tide>();
		
		query(GETTIDESFORDAY_QUERY, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setLong(1, start.getEpochSecond());
				stmt.setLong(2, end.getEpochSecond());
			}

			public void row(ResultSet rs, int irow) throws Exception {
				Tide tide = Tide.fromRow(rs, cfg);
				tides.add(tide);
			}
		});

		return(tides);
	}

	// +----------+
	// | saveTide |
	// +----------+

	private final static String INSERT_TIDE =
		"insert into tides " +
		"(tide_id, epoch_second, minute_of_day, day_of_year, tide_height, " +
		" uv, lux, wind_mps, temp_f, pressure_mb, humidity) " +
		"values (?,?,?,?,?,?,?,?,?,?,?) ";

	public Tide saveTide(Tide t, File jpegFile) throws Exception {

		if (cfg.FilesPath == null) throw new Exception("files not configured");
		
		t.TideId = UUID.randomUUID().toString();
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(INSERT_TIDE, new SqlStore.UpdateHandler() {

			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, t.TideId);
				stmt.setLong(2, t.EpochSecond);
				stmt.setInt(3, t.MinuteOfDay);
				stmt.setInt(4, t.DayOfYear);
				stmt.setDouble(5, t.TideHeight);
				stmt.setDouble(6, t.WeatherMetrics.UV);
				stmt.setDouble(7, t.WeatherMetrics.Lux);
				stmt.setDouble(8, t.WeatherMetrics.WindMps);
				stmt.setDouble(9, t.WeatherMetrics.TempF);
				stmt.setDouble(10, t.WeatherMetrics.PressureMb);
				stmt.setDouble(11, t.WeatherMetrics.Humidity);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) return(null);

		try {
			t.ImageFile = fileForTide(t, cfg);
			Files.copy(jpegFile.toPath(), t.ImageFile.toPath());
		}
		catch (IOException e) {

			log.severe(Easy.exMsg(e, "saveTide", true));
			
			try { deleteTide(t); }
			catch (Exception eDel) { /* eat it */ }
			
			return(null);
		}

		return(t);
	}

	// +------------+
	// | deleteTide |
	// +------------+

	private final static String DELETE_TIDE =
		"delete from tides where tide_id = ?";
		
	public void deleteTide(Tide t) throws Exception {

		if (cfg.FilesPath == null) throw new Exception("files not configured");

		update(DELETE_TIDE, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, t.TideId);
			}
		});

		fileForTide(t, cfg).delete();
	}
	
	// +-----+
	// | DDL |
	// +-----+

	private final static String CREATE_TIDES_TABLE =
		"create table tides " +
		"( " +
		"    tide_id char(36) not null, " +
		"    epoch_second integer not null, " +
		"    minute_of_day integer not null, " +
		"    day_of_year integer not null, " +
		"    tide_height double not null, " +
		"    uv double not null, " +
		"    lux double not null, " +
		"    wind_mps double not null, " +
		"    temp_f double not null, " +
		"    pressure_mb double not null, " +
		"    humidity double not null, " +
		" " +
		"    primary key (tide_id) " +
		") ";

	private void ensureTables() throws Exception {
		ensureTable("tides", CREATE_TIDES_TABLE);
	}

	// +-------------+
	// | urlForTide  |
	// | fileForTide |
	// +-------------+

	public static class TideFileParts
	{
		String Year;
		String Month;
		String Day;
		String File;
	}

	private static TideFileParts filePartsForTide(Tide t) {

		TideFileParts parts = new TideFileParts();
		
		String iso8601 = Instant.ofEpochSecond(t.EpochSecond).toString();
		
		parts.Year = iso8601.substring(0, 4);
		parts.Month = iso8601.substring(5, 7);
		parts.Day = iso8601.substring(8, 10);
		parts.File = String.format("%02d-%s.jpg", t.MinuteOfDay, t.TideId);

		return(parts);
	}

	public String urlForTide(Tide t) throws Exception {

		if (cfg.ImageUrlPrefix == null) throw new Exception("url not configured");

		TideFileParts parts = filePartsForTide(t);

		String url = cfg.ImageUrlPrefix;
		url = Easy.urlPaste(url, parts.Year);
		url = Easy.urlPaste(url, parts.Month);
		url = Easy.urlPaste(url, parts.Day);
		url = Easy.urlPaste(url, parts.File);

		return(url);
	}
	
	private static File fileForTide(Tide t, Config cfg) {

		if (cfg.FilesPath == null) return(null);
		
		TideFileParts parts = filePartsForTide(t);
		
		File file = Paths.get(cfg.FilesPath, parts.Year, parts.Month,
							  parts.Day, parts.File).toFile();

		file.getParentFile().mkdirs();
		
		return(file);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static Logger log = Logger.getLogger(TideStore.class.getName());
}
