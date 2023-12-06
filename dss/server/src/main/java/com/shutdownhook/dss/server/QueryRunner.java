/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class QueryRunner
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public QueryRunner() {
		stores = new HashMap<String,SqlStore>();
	}

	// +--------+
	// | Result |
	// +--------+

	public static class Result
	{
		public List<String> Headers = new ArrayList<String>();
		public List<List<String>> Rows = new ArrayList<List<String>>();
		public Integer UpdateCount;
	}
	
	public static class QueryResults
	{
		public List<Result> Results = new ArrayList<Result>();
		public String Error;
	}

	// +-----+
	// | run |
	// +-----+

	public QueryResults run(String connectionString,
							String sql,
							String[] params) throws Exception {

		QueryResults results;

		try {
			results = runHelper(connectionString, sql, params);
		}
		catch (SQLException e) {
			results = new QueryResults();
			results.Error = e.getMessage();
			if (results.Error == null) results.Error = e.toString();
		}

		return(results);
	}

	private QueryResults runHelper(String connectionString,
								   String sql,
								   String[] params) throws Exception {

		SqlStore store = getStore(connectionString);
		QueryResults results = new QueryResults();

		store.execute(sql, new SqlStore.ExecuteHandler() {

			private int lastResultIndex = -1;
			private Result currentResult = null;
				
			public void prepare(PreparedStatement stmt) throws Exception {
				if (params == null) return;
				for (int i = 0; i < params.length; ++i) {
					String p = params[i];
					try { stmt.setInt(i+1, Integer.parseInt(p)); }
					catch (Exception eParse) { stmt.setString(i+1, p); }
				}
			}

			public void update(int count, int iresult) throws Exception {
				lastResultIndex = iresult;
				currentResult = new Result();
				results.Results.add(currentResult);
				currentResult.UpdateCount = count;
			}
				
			public void row(ResultSet rs, int irow, int iresult) throws Exception {
				if (iresult != lastResultIndex) {
					lastResultIndex = iresult;
					currentResult = new Result();
					results.Results.add(currentResult);
					addHeaders(rs);
				}
				
				addRow(rs);
			}

			private void addHeaders(ResultSet rs) throws SQLException {
				ResultSetMetaData meta = rs.getMetaData();
				int ccol = meta.getColumnCount();
				for (int icol = 1; icol <= ccol; ++icol) {
					currentResult.Headers.add(meta.getColumnLabel(icol));
				}
			}
				
			private void addRow(ResultSet rs) throws SQLException {
				List<String> row = new ArrayList<String>();
				currentResult.Rows.add(row);
				for (int icol = 1; icol <= currentResult.Headers.size(); ++icol) {
					row.add(rs.getString(icol));
				}
			}
		});

		return(results);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private synchronized SqlStore getStore(String connectionString) {

		if (!stores.containsKey(connectionString)) {
			SqlStore.Config cfg = new SqlStore.Config(connectionString);
			stores.put(connectionString, new SqlStore(cfg));
		}

		return(stores.get(connectionString));
	}

	// +---------+
	// | Members |
	// +---------+

	private Map<String,SqlStore> stores;

	private final static Logger log = Logger.getLogger(QueryRunner.class.getName());
}
