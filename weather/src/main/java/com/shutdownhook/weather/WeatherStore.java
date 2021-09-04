/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.shutdownhook.toolbox.SqlStore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class WeatherStore
{
	public WeatherStore(SqlStore sql) throws Exception {
		this.sql = sql;
		ensureTables();
	}

	// +----------+
	// | Stations |
	// +----------+

	public static class Station
	{
		public String StationId;
		public String DeviceId;
		public String AccessToken;
		public String Name;
		public Double Latitude;
		public Double Longitude;

		public static Station fromRow(ResultSet rs) throws SQLException {
			Station s = new Station();
			s.StationId = rs.getString("station_id");
			s.DeviceId = rs.getString("device_id");
			s.AccessToken = rs.getString("access_token");
			s.Name = rs.getString("name");
			s.Latitude = rs.getDouble("latitude");
			s.Longitude = rs.getDouble("longitude");
			return(s);
		}

		@Override
		public String toString() {
			return(new GsonBuilder().setPrettyPrinting().create().toJson(this));
		}
	}

	public List<Station> listStations() throws Exception {
		
		List<Station> stations = new ArrayList<Station>();
		
		sql.query(QUERY_STATIONS, new SqlStore.QueryHandler() {
				
			public void row(ResultSet rs, int irow) throws Exception {
				stations.add(Station.fromRow(rs));
			}
		});

		return(stations);
	}

	public Station getStation(String stationId) throws Exception {

		SqlStore.Return<Station> station = new SqlStore.Return<Station>();
		
		sql.query(QUERY_STATION, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, stationId);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception(">1 found for " + stationId);
				station.Value = Station.fromRow(rs);
			}
		});

		return(station.Value);
	}

	public boolean addStation(Station station) throws Exception {

		String stmt = (getStation(station.StationId) == null ? INSERT_STATION : UPDATE_STATION);
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		sql.update(stmt, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, station.DeviceId);
				stmt.setString(2, station.AccessToken);
				stmt.setString(3, station.Name);
				stmt.setDouble(4, station.Latitude);
				stmt.setDouble(5, station.Longitude);
				stmt.setString(6, station.StationId);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value == true);
	}
	
	// +---------+
	// | Metrics |
	// +---------+

	public static class Metrics
	{
		// this special field lets the metrics table do double-duty. Whenever we get 
		// a measurement from the device itself, we leave the value as "0" to indicate
		// that it's a direct measurement. For rollups, we use a value here to 
		// represent the timespan of the aggregation (60 = 1 hour, 1440 = 1 day, etc.)
		public Integer AggregationMinutes = 0;

		public String StationId;
		public Instant EpochTime;
		public Integer SpanMinutes;
		public Double TempF;
		public Double PressureMb;
		public Double HumidityPct;
		public Double PrecipIn;
		public Double WindMph;
		public Double WindMaxMph;
		public Double SolarRadWpm2;

		public static Metrics fromRow(ResultSet rs) throws SQLException {
			Metrics m = Metrics.fromPartialRow(rs);
			m.StationId = rs.getString("station_id");
			m.EpochTime = Instant.ofEpochSecond(rs.getLong("epoch_seconds"));
			m.AggregationMinutes = rs.getInt("aggregation_minutes");
			return(m);
		}

		public static Metrics fromAggregateRow(String stationId, Instant epochStart,
											   int aggregationMinutes, ResultSet rs)
			throws SQLException {
			
			Metrics m = Metrics.fromPartialRow(rs);
			m.StationId = stationId;
			m.EpochTime = epochStart;
			m.AggregationMinutes = aggregationMinutes;
			return(m);
		}
		
		private static Metrics fromPartialRow(ResultSet rs) throws SQLException {
			Metrics m = new Metrics();
			m.SpanMinutes = rs.getInt("span_minutes");
			m.TempF = rs.getDouble("temp_f");
			m.PressureMb = rs.getDouble("pressure_mb");
			m.HumidityPct = rs.getDouble("humidity_pct");
			m.PrecipIn = rs.getDouble("precip_in");
			m.WindMph = rs.getDouble("wind_mph");
			m.WindMaxMph = rs.getDouble("wind_max_mph");
			m.SolarRadWpm2 = rs.getDouble("solar_rad_wpm2");
			return(m);
		}

		@Override
		public String toString() {
			return(new GsonBuilder().setPrettyPrinting().create().toJson(this));
		}
	}

	public boolean addMetrics(Metrics metrics) throws Exception {

		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		
		sql.update(INSERT_METRICS, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, metrics.StationId);
				stmt.setLong(2, metrics.EpochTime.getEpochSecond());
				stmt.setInt(3, metrics.AggregationMinutes);
				stmt.setInt(4, metrics.SpanMinutes);
				stmt.setDouble(5, metrics.TempF);
				stmt.setDouble(6, metrics.PressureMb);
				stmt.setDouble(7, metrics.HumidityPct);
				stmt.setDouble(8, metrics.PrecipIn);
				stmt.setDouble(9, metrics.WindMph);
				stmt.setDouble(10, metrics.WindMaxMph);
				stmt.setDouble(11, metrics.SolarRadWpm2);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value == true);
	}

	public List<Metrics> getMetrics(String stationId, Instant minEpoch,
									Instant maxEpoch, Integer limit) throws Exception {

		String stationClause = "";
		String minClause = "";
		String maxClause = "";
		String limitClause = "";

		if (stationClause != null) {
			stationClause = String.format(QUERY_METRICS_STATION_CLAUSE_FMT, stationId);
		}
		
		if (minEpoch != null) {
			minClause = String.format(QUERY_METRICS_TIME_CLAUSE_FMT,
									  ">=", minEpoch.getEpochSecond());
		}

		if (maxEpoch != null) {
			maxClause = String.format(QUERY_METRICS_TIME_CLAUSE_FMT,
									  "<=", maxEpoch.getEpochSecond());
		}

		if (limit != null) {
			limitClause = String.format(QUERY_METRICS_LIMIT_CLAUSE_FMT, limit);
		}

		String query = String.format(QUERY_METRICS_FMT, stationClause, minClause,
									 maxClause, limitClause);
		
		List<Metrics> metrics = new ArrayList<Metrics>();
		
		sql.query(query, new SqlStore.QueryHandler() {
				
			public void row(ResultSet rs, int irow) throws Exception {
				metrics.add(Metrics.fromRow(rs));
			}
		});

		return(metrics);
	}
									
	// +-------------+
	// | Aggregation |
	// +-------------+

	public Metrics computeAggregateMetrics(String stationId, Instant startEpoch,
										   int aggregationMinutes, int minimumCoveragePct)
		throws Exception {

		SqlStore.Return<Metrics> metrics = new SqlStore.Return<Metrics>();
		
		sql.query(QUERY_AGGREGATE_METRICS, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, stationId);
				stmt.setLong(2, startEpoch.getEpochSecond());
				stmt.setLong(3, startEpoch.getEpochSecond());
				stmt.setInt(4, aggregationMinutes);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception(">1 found agg for " + stationId);
				metrics.Value = Metrics.fromAggregateRow(stationId, startEpoch,
														 aggregationMinutes, rs);
			}
		});

		int coveragePct = (metrics.Value.SpanMinutes * 100 / metrics.Value.AggregationMinutes);
		return(coveragePct >= minimumCoveragePct ? metrics.Value : null);
	}

	// +---------+
	// | Pruning |
	// +---------+

	public void pruneGranularMetrics(Instant epochMaxAge) throws Exception {

		sql.update(PRUNE_METRICS, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setLong(1, epochMaxAge.getEpochSecond());
			}
		});
	}

	// +-------+
	// | Setup |
	// +-------+

	private void ensureTables() throws Exception {
		ensureTable("stations", CREATE_STATIONS_TABLE);
		ensureTable("metrics", CREATE_METRICS_TABLE);
	}

	private void ensureTable(String tableName, String ddl) throws Exception {
		if (!tableExists(tableName)) {
			sql.update(ddl);
		}
	}

	private boolean tableExists(String tableName) throws Exception {

		boolean exists = false;
		
		try {
			sql.query("select 1 from " + tableName + " limit 1", new SqlStore.QueryHandler() {
				public void row(ResultSet rs, int irow) throws Exception { }
			});
			
			exists = true;
		}
		catch (Exception e) {
			// nothing
		}

		return(exists);
	}

	// +-----+
	// | SQL |
	// +-----+

	// Stations

	private final static String QUERY_STATIONS =
		"select * from stations";

	private final static String QUERY_STATION =
		QUERY_STATIONS + " where station_id = ?";

	private final static String INSERT_STATION =
		"insert into stations " +
		"(device_id, access_token, name, latitude, longitude, station_id) " +
		"values (?,?,?,?,?,?) ";

	private final static String UPDATE_STATION =
		"update into stations s " +
		"set device_id = ?, access_token = ?, name = ?, " +
		"    latitude = ?, longitude = ? where station_id = ? ";

	// Metrics

	private final static String INSERT_METRICS =
		"insert into metrics " +
		"(station_id, epoch_seconds, aggregation_minutes, span_minutes, temp_f, " +
		" pressure_mb, humidity_pct, precip_in, wind_mph, wind_max_mph, solar_rad_wpm2) " +
		"values (?,?,?,?,?,?,?,?,?,?,?)";

	private final static String QUERY_METRICS_FMT =
		"select " +
		"	span_minutes, " +
		"	temp_f, " +
		"	pressure_mb, " +
		"	humidity_pct, " +
		"	precip_in, " +
		"	wind_mph, " +
		"	wind_max_mph, " +
		"	solar_rad_wpm2 " +
		"from " +
		"	metrics m " +
		"where " +
		"	m.aggregation_minutes = 0 " +
		" %s %s %s " +
		"order by " +
		"   m.epoch_seconds desc ";

	private final static String QUERY_METRICS_STATION_CLAUSE_FMT =
		"and m.station_id = %s ";

	private final static String QUERY_METRICS_TIME_CLAUSE_FMT =
		"and m.epoch_seconds %s %d ";
	
	private final static String QUERY_METRICS_LIMIT_CLAUSE_FMT =
		"limit %d ";
		
	private final static String QUERY_AGGREGATE_METRICS =
		"select " +
		"	sum(span_minutes) span_minutes, " +
		"	avg(temp_f) temp_f, " +
		"	avg(pressure_mb) pressure_mb, " +
		"	avg(humidity_pct) humidity_pct, " +
		"	sum(precip_in) precip_in, " +
		"	avg(wind_mph) wind_mph, " +
		"	max(wind_max_mph) wind_max_mph, " +
		"	avg(solar_rad_wpm2) solar_rad_wpm2 " +
		"from " +
		"	metrics m " +
		"where " +
		"	m.station_id = ? and " +
		"	m.aggregation_minutes = 0 and " +
		"	m.epoch_seconds >= ? and " +
		"	m.epoch_seconds < ? + (? * 60) ";

	private final static String PRUNE_METRICS =
		"delete from metrics where aggregation_minutes = 0 and epoch_seconds < ?";
	
	// DDL
	
	private final static String CREATE_STATIONS_TABLE =
		"create table stations " +
		"( " +
		"    station_id varchar(32) not null, " +
		"    device_id varchar(32) not null, " +
		"    access_token varchar(36) not null, " +
		"    name varchar(64) null, " +
		"    latitude float null, " +
		"    longitude float null, " +
		" " +
		"    primary key (station_id) " +
		") ";

	private final static String CREATE_METRICS_TABLE =
		"create table metrics " +
		"( " +
		"    station_id varchar(32) not null, " +
		"    epoch_seconds integer not null, " +
		"    aggregation_minutes integer not null, " +
		"    span_minutes integer not null, " +
		"    temp_f float null, " +
		"    pressure_mb float null, " +
		"    humidity_pct float null, " +
		"    precip_in float null, " +
		"    wind_mph float null, " +
		"    wind_max_mph float null, " +
		"    solar_rad_wpm2 float null, " +
		" " +
		"    primary key (station_id, epoch_seconds, aggregation_minutes) " +
		") ";

	// +---------+
	// | Members |
	// +---------+

	private SqlStore sql;
}
