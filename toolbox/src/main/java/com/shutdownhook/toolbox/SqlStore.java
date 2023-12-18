/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
		public String[] PreloadDrivers;
	}

	public SqlStore(Config cfg) {
		this.cfg = cfg;
		preloadDrivers();
	}

	private void preloadDrivers() {
		
		if (cfg.PreloadDrivers == null) return;
		
		for (String driver : cfg.PreloadDrivers) {
			try { Class.forName(driver); }
			catch (Exception e) { log.severe(Easy.exMsg(e, driver, false));	}
		}
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
				
			public boolean row(ResultSet rs, int irow, int iresult) throws Exception {
				if (iresult == 0) handler.row(rs, irow);
				return(true);
			}
		});
	}

	// +---------+
	// | execute |
	// +---------+
	
	public interface ExecuteHandler {
		default public void prepare(PreparedStatement stmt) throws Exception { } 
		default public boolean row(ResultSet rs, int irow, int iresult) throws Exception { return(false); }
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
					boolean keepGoing = true;
					while (keepGoing && rs != null && rs.next()) {
						keepGoing = handler.row(rs, irow++, iresult);
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

	// +--------+
	// | schema |
	// +--------+

	public static class TableInfo
	{
		public String Name;
		public List<ColumnInfo> Columns = new ArrayList<ColumnInfo>();
	}
	
	public static class ColumnInfo
	{
		public String Name;
		public String Type;
		public Boolean Nullable;
	}
	
	public List<TableInfo> getTableInfo() throws Exception {

		Connection cxn = null;
		ResultSet rsTables = null;
		ResultSet rsColumns = null;

		List<TableInfo> tables = new ArrayList<TableInfo>();

		try {
			cxn = getConnection();
			DatabaseMetaData metaData = cxn.getMetaData();
			rsTables = metaData.getTables(null, null, null, new String[] { "TABLE" });

			while (rsTables.next()) {
				
				TableInfo tableInfo = new TableInfo();
				tables.add(tableInfo);

				String catalog = rsTables.getString("TABLE_CAT");
				String schema = rsTables.getString("TABLE_SCHEM");
				String name = rsTables.getString("TABLE_NAME");

				String fullName = String.format("%s%s%s",
												(catalog == null ? "" : catalog + "."),
												(schema == null ? "" : schema + "."),
												name);

				tableInfo.Name = fullName;

				rsColumns = metaData.getColumns(catalog, schema, name, null);

				while (rsColumns.next()) {

					ColumnInfo columnInfo = new ColumnInfo();
					tableInfo.Columns.add(columnInfo);

					columnInfo.Name = rsColumns.getString("COLUMN_NAME");
					columnInfo.Type = rsColumns.getString("TYPE_NAME");

					short nullable = rsColumns.getShort("NULLABLE");
					columnInfo.Nullable = (nullable == DatabaseMetaData.attributeNullable);
				}

				rsColumns.close();
			}
		}
		finally {
			if (rsColumns != null) rsColumns.close();
			if (rsTables != null) rsTables.close();
			if (cxn != null) cxn.close();
		}

		return(tables);
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
