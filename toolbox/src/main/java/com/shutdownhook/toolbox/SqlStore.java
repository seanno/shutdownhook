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

	// +-------+
	// | query |
	// +-------+

	public interface QueryHandler {
		default public void prepare(PreparedStatement stmt) throws Exception { } 
		public void row(ResultSet rs, int irow) throws Exception;
	}

	public void query(String sql, QueryHandler handler) throws Exception {

		Connection cxn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			cxn = getConnection();
			stmt = cxn.prepareStatement(sql);
			handler.prepare(stmt);

			rs = stmt.executeQuery();
			int irow = 0;
			
			while (rs != null && rs.next()) {
				handler.row(rs, irow++);
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
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(SqlStore.class.getName());
}
