/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class SqlStore
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public Config() {
			// nut-n-honey
		}
		
		public Config(String connectionString) {
			this.ConnectionString = connectionString;
		}
		
		public String ConnectionString;
	}

	public SqlStore(Config cfg) {
		this.cfg = cfg;
	}
	
	// +---------------+
	// | getConnection |
	// +---------------+

	// this is a basic, non-pooled connection builder. Override it to provide
	// useful stuff like connection pooling or additional configuration.
	// The code will get/close connections using this method on each operation.
	// Plan is to add to the config and base implementation over time but
	// this leaves us a reasonable extension point.
	
	public Connection getConnection() throws SQLException {
		return(DriverManager.getConnection(cfg.ConnectionString));
	}

	// +--------+
	// | Return |
	// +--------+

	// Convenience class to wrap return values from anon handler classes
	
	public static class Return<T>
	{
		public T Value = null;
	}

	// +-------+
	// | query |
	// +-------+

	// select statement returning a single result set
	
	public interface QueryHandler {
		default public void prepare(PreparedStatement stmt) throws Exception { } 
		public void row(ResultSet rs, int irow) throws Exception;
	}

	public void query(String sql, QueryHandler handler) throws Exception {

		execute(sql, new ExecuteHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				handler.prepare(stmt);
			}
				
			public void row(ResultSet rs, int irow, int iresult) throws Exception {
				if (iresult == 0) handler.row(rs, irow);
			}
		});
	}

	// +---------+
	// | execute |
	// +---------+
	
	public interface ExecuteHandler {
		default public void prepare(PreparedStatement stmt) throws Exception { } 
		default public void row(ResultSet rs, int irow, int iresult) throws Exception { }
		default public void update(int count, int iresult) throws Exception { }
	}

	public void execute(String sql, ExecuteHandler handler) throws Exception {

		Connection cxn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			cxn = getConnection();
			stmt = cxn.prepareStatement(sql);
			handler.prepare(stmt);

			boolean isResultSet = stmt.execute();
			int irow = 0;
			int iresult = 0;
			
			while (true) {
				
				if (isResultSet) {
					// iterate result set
					rs = stmt.getResultSet();
					while (rs != null && rs.next()) {
						handler.row(rs, irow++, iresult);
					}
				}
				else {
					int updateCount = stmt.getUpdateCount();
					if (updateCount == -1) {
						// no more results
						break;
					}

					// tell handler about update 
					handler.update(updateCount, iresult);
				}

				++iresult;
				isResultSet = stmt.getMoreResults();
			}
		}
		finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
			if (cxn != null) cxn.close();
		}
	}

	// +--------+
	// | update |
	// +--------+

	public interface UpdateHandler {
		default public boolean proceed(int iter) { return(iter == 0); }
		default public void prepare(PreparedStatement stmt, int iter) throws Exception { }
		default public void confirm(int rowsAffected, int iter) { }
	}

	public int update(String sql) throws Exception {
		return(update(sql, new UpdateHandler() { } ));
	}
	
	public int update(String sql, UpdateHandler handler) throws Exception {

		Connection cxn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try {
			cxn = getConnection();
			stmt = cxn.prepareStatement(sql);

			boolean proceed = true;
			int iter = 0;
			int ret = 0;
			
			while (proceed = handler.proceed(iter)) {
				handler.prepare(stmt, iter);
				ret = stmt.executeUpdate();
				handler.confirm(ret, iter);
				++iter;
			}

			return(ret);
		}
		finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
			if (cxn != null) cxn.close();
		}
	}
	
	// +---------+
	// | execute |
	// +---------+

	public boolean execute(String sql) throws Exception {

		Connection cxn = null;
		Statement stmt = null;

		try {
			cxn = getConnection();
			stmt = cxn.createStatement();
			return(stmt.execute(sql));
		}
		finally {
			if (stmt != null) stmt.close();
			if (cxn != null) cxn.close();
		}
	}
	
	// +-------------+
	// | DDL Support |
	// +-------------+

	public void ensureTable(String tableName, String ddl) throws Exception {
		if (!tableExists(tableName)) update(ddl);
	}

	public boolean tableExists(String tableName) {

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

	// +---------------------+
	// | Convenience Helpers |
	// +---------------------+

	public static Integer getNullableInt(ResultSet rs, String field)
		throws SQLException {
		
		int i = rs.getInt(field);
		return(rs.wasNull() ? null : i);
	}

	// +---------+
	// | Members |
	// +---------+

	protected Config cfg;
	
	private final static Logger log = Logger.getLogger(SqlStore.class.getName());
}
