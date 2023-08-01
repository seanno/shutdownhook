/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.UUID;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class TidesStore extends SqlStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public SqlStore.Config Sql;
		public String FilesPath;
	}

	public TidesStore(Config cfg) throws Exception {
		super(cfg.Sql);
		this.cfg = cfg;
		ensureTables();
		Files.createDirectories(Paths.get(cfg.FilesPath));
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

		public static Tide fromRow(ResultSet rs) throws SQLException {
			Tide t = new Tide();
			t.TideId = rs.getString("tide_id");
			t.EpochSecond = rs.getLong("epoch_second");
			t.HourOfDay = rs.getInt("hour_of_day");
			t.DayOfYear = rs.getInt("day_of_year");
			t.TideHeight = rs.getDouble("tide_height");
			t.UV = rs.getDouble("uv");
			return(t);
		}
	}

	// +----------+
	// | saveTide |
	// +----------+

	public String saveTide(Tide t, Path jpegPath) {

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
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		if (!added.Value) return(null);

		try {
			Path targetPath = pathForTide(t);
			Files.createDirectories(targetPath.getParent());
			Files.copy(jpegPath, targetPath);
		}
		catch (IOException e) {

			try { deleteTide(t); }
			catch (Exception eDel) { /* eat it */ }
			
			return(null);
		}

		return(t.TideId);
	}

	// +------------+
	// | deleteTide |
	// +------------+

	public void deleteTide(Tide t) {

		update(DELETE_TIDE, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, t.TideId);
			}
		});

		Files.delete(pathForTide(t));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private Path pathForTide(Tide t) {

		String iso8601 = Instant.ofEpochSecond(t.EpochSecond).toString();
		
		String year = iso8601.substring(0, 4);
		String month = iso8601.substring(5, 7);
		String day = iso8601.substring(8, 10);
		
		return(Paths.get(cfg.FilesPath, year, month, day,
						 String.format("%02d-%s.jpg", t.HourOfDay, t.TideId)));
	}

	private void ensureTables() throws Exception {
		ensureTable("tides", CREATE_TIDES_TABLE);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;

	private final static Logger log = Logger.getLogger(TidesStore.class.getName());

	// +-----+
	// | SQL |
	// +-----+
 
	private final static String INSERT_TIDE =
		"insert into tides " +
		"(tide_id, epoch_second, hour_of_day, day_of_year, tide_height, uv) " +
		"values (?,?,?,?,?,?,?) ";

	private final static String DELETE_TIDE =
		"delete from tides where tide_id = ?";
		
	private final static String CREATE_TIDES_TABLE =
		"create table tides " +
		"( " +
		"    tide_id char(36) not null, " +
		"    epoch_second integer not null, " +
		"    hour_of_day integer not null, " +
		"    day_of_year integer not null, " +
		"    tide_height double not null, " +
		"    uv double not null, " +
		" " +
		"    primary key (tide_id) " +
		") ";

}
