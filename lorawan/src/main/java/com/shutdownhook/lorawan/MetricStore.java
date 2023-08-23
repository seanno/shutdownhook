/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan;

import java.lang.StringBuilder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class MetricStore extends SqlStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public MetricStore(SqlStore.Config cfg) throws Exception {
		super(cfg);
		ensureTables();
	}

	// +------------+
	// | saveMetric |
	// +------------+

	private final static String INSERT_METRIC =
		"insert into metrics " +
		"(epoch_second, name, value) " +
		"values (?,?,?) ";
	
	public boolean saveMetric(String name, double value) throws Exception {
		return(saveMetric(name, value, Instant.now().getEpochSecond()));
	}

	public boolean saveMetric(String name, double value, long epochSecond) throws Exception {
		
		SqlStore.Return<Boolean> added = new SqlStore.Return<Boolean>();
		added.Value = false;
		
		update(INSERT_METRIC, new SqlStore.UpdateHandler() {

			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setLong(1, epochSecond);
				stmt.setString(2, name);
				stmt.setDouble(3, value);
			}
				
			public void confirm(int rowsAffected, int iter) {
				if (rowsAffected > 0) added.Value = true;
			}
		});

		return(added.Value);
	}

	// +------------+
	// | getMetrics |
	// +------------+

	static public class Metric
	{
		public String When;
		public Double Value;
	}

	private final static String QUERY_METRICS =
		"select epoch_second, value from metrics where name = ? ";

	private final static String START_CLAUSE =
		"and epoch_second >= ? ";
	
	private final static String END_CLAUSE =
		"and epoch_second <= ? ";
	
	private final static String SORT_CLAUSE =
		"order by epoch_second desc ";

	private final static String MAX_CLAUSE =
		"limit ? ";

	public List<Metric> getMetrics(String name, Instant start,
								   Instant end, Integer maxCount,
								   ZoneId tz) throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append(QUERY_METRICS);
		if (start != null) sb.append(START_CLAUSE);
		if (end != null) sb.append(END_CLAUSE);
		sb.append(SORT_CLAUSE);
		if (maxCount != null) sb.append(MAX_CLAUSE);

		List<Metric> metrics = new ArrayList<Metric>();
		
		query(sb.toString(), new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {

				int i = 1;
				stmt.setString(i++, name);
				if (start != null) stmt.setLong(i++, start.getEpochSecond());
				if (end != null) stmt.setLong(i++, end.getEpochSecond());
				if (maxCount != null) stmt.setLong(i++, maxCount);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {

				Metric metric = new Metric();
				metrics.add(metric);
				metric.Value = rs.getDouble("value");
				
				metric.When = Instant
					.ofEpochSecond(rs.getLong("epoch_second"))
					.atZone(tz)
					.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			}
		});

		return(metrics);
	}

	// +-----+
	// | DDL |
	// +-----+

	private final static String CREATE_METRICS_TABLE =
		"create table metrics " +
		"( " +
		"    epoch_second integer not null, " +
		"    name varchar(64) not null, " +
		"    value double not null, " +
		" " +
		"    primary key (epoch_second, name) " +
		") ";

	private void ensureTables() throws Exception {
		ensureTable("metrics", CREATE_METRICS_TABLE);
	}

	// +---------+
	// | Helpers |
	// +---------+

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(MetricStore.class.getName());
}
