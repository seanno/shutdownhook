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
		public String FilesPath;
	}

	public TideStore(Config cfg) throws Exception {
		super(cfg.Sql);
		this.cfg = cfg;
		ensureTables();
		new File(cfg.FilesPath).mkdirs();
	}

	// +------+
	// | Tide |
	// +------+

	public static class Tide
	{
		public String TideId;
		public Long EpochSecond;
		public Integer HourOfDay;
		public Integer DayOfYear;
		public double TideHeight;
		public double UV;
		public double Lux;
		public double WindMps;
		public double TempF;
		public double PrecipMmph;
		public double PressureMb;
		public double Humidity;
		public File ImageFile;

		public static Tide fromRow(ResultSet rs) throws SQLException {
			Tide t = new Tide();
			t.TideId = rs.getString("tide_id");
			t.EpochSecond = rs.getLong("epoch_second");
			t.HourOfDay = rs.getInt("hour_of_day");
			t.DayOfYear = rs.getInt("day_of_year");
			t.TideHeight = rs.getDouble("tide_height");
			t.UV = rs.getDouble("uv");
			t.Lux = rs.getDouble("lux");
			t.WindMps = rs.getDouble("wind_mps");
			t.TempF = rs.getDouble("temp_f");
			t.PrecipMmph = rs.getDouble("precip_mmph");
			t.PressureMb = rs.getDouble("pressure_mb");
			t.Humidity = rs.getDouble("humidity");
			return(t);
		}
	}

	// +--------------+
	// | queryClosest |
	// +--------------+

	// this query uses thresholds --- if zero results come back, or the
	// "best" match isn't very good, the caller will have to loosen the
	// thresholds and try again. The point is to try to have some options
	// to choose from, but still be reasonably close.
	
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
		"        when abs(hour_of_day - ?) > (24 / 2) " +
		"        then 24 - abs(hour_of_day - ?) " +
		"        else abs(hour_of_day - ?) " +
		"    end <= ? " +
		"order by " +
		"    abs(tide_height - ?) " +
		"limit " +
		"    ? ";

	static public class ClosestTriple
	{
		public ClosestTriple(double height, long hours, long days) {
			this.Height = height;
			this.Hours = hours;
			this.Days = days;
		}

		public double Height;
		public long Hours;
		public long Days;
	}

	public List<Tide> queryClosest(ClosestTriple match,
								   ClosestTriple[] progressiveThresholds,
								   int maxResults) throws Exception {
		
		for (ClosestTriple thresholds : progressiveThresholds) {
			List<Tide> tides = queryClosest(match, thresholds, maxResults);
			if (tides.size() > 0) return(tides);
		}

		return(null);
	}

	public List<Tide> queryClosest(ClosestTriple match,
								   ClosestTriple thresholds,
								   int maxResults) throws Exception {

		List<Tide> tides = new ArrayList<Tide>();
		
		query(CLOSEST_QUERY, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				
				stmt.setDouble(1, match.Height);
				stmt.setDouble(2, thresholds.Height);
				
				stmt.setLong(3, match.Days);
				stmt.setLong(4, match.Days);
				stmt.setLong(5, match.Days);
				stmt.setLong(6, thresholds.Days);
				
				stmt.setLong(7, match.Hours);
				stmt.setLong(8, match.Hours);
				stmt.setLong(9, match.Hours);
				stmt.setLong(10, thresholds.Hours);

				stmt.setDouble(11, match.Height);

				stmt.setLong(12, maxResults);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {

				Tide tide = Tide.fromRow(rs);
				tide.ImageFile = fileForTide(tide);
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
		"(tide_id, epoch_second, hour_of_day, day_of_year, tide_height, " +
		" uv, lux, wind_mps, temp_f, precip_mmph, pressure_mb, humidity) " +
		"values (?,?,?,?,?,?,?,?,?,?,?,?) ";

	public Tide saveTide(Tide t, File jpegFile) throws Exception {

		t.TideId = UUID.randomUUID().toString();
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		update(INSERT_TIDE, new SqlStore.UpdateHandler() {

			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, t.TideId);
				stmt.setLong(2, t.EpochSecond);
				stmt.setInt(3, t.HourOfDay);
				stmt.setInt(4, t.DayOfYear);
				stmt.setDouble(5, t.TideHeight);
				stmt.setDouble(6, t.UV);
				stmt.setDouble(7, t.Lux);
				stmt.setDouble(8, t.WindMps);
				stmt.setDouble(9, t.TempF);
				stmt.setDouble(10, t.PrecipMmph);
				stmt.setDouble(11, t.PressureMb);
				stmt.setDouble(12, t.Humidity);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) return(null);

		try {
			t.ImageFile = fileForTide(t);
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

		update(DELETE_TIDE, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, t.TideId);
			}
		});

		fileForTide(t).delete();
	}
	
	// +-----+
	// | DDL |
	// +-----+

	private final static String CREATE_TIDES_TABLE =
		"create table tides " +
		"( " +
		"    tide_id char(36) not null, " +
		"    epoch_second integer not null, " +
		"    hour_of_day integer not null, " +
		"    day_of_year integer not null, " +
		"    tide_height double not null, " +
		"    uv double not null, " +
		"    lux double not null, " +
		"    wind_mps double not null, " +
		"    temp_f double not null, " +
		"    precip_mmph double not null, " +
		"    pressure_mb double not null, " +
		"    humidity double not null, " +
		" " +
		"    primary key (tide_id) " +
		") ";

	private void ensureTables() throws Exception {
		ensureTable("tides", CREATE_TIDES_TABLE);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private File fileForTide(Tide t) {

		String iso8601 = Instant.ofEpochSecond(t.EpochSecond).toString();
		
		String year = iso8601.substring(0, 4);
		String month = iso8601.substring(5, 7);
		String day = iso8601.substring(8, 10);

		String fileName = String.format("%02d-%s.jpg", t.HourOfDay, t.TideId);
		File file = Paths.get(cfg.FilesPath, year, month, day, fileName).toFile();
		file.getParentFile().mkdirs();
		
		return(file);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static Logger log = Logger.getLogger(TideStore.class.getName());
}
