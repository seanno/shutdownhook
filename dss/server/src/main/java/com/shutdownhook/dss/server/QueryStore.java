/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Access notes:
// "access.is_editor" on a connection means that a user is allowed to create
// and save queries. Anybody with access to the connection can run these queries,
// but only the owner can edit them (through the UX).
// anybody given access to the DSS Metadata Store can of course edit these
// tables directly to do whatever they want with them, including seeing the
// saved connection strings.
//
// add logging by query execution and user!


package com.shutdownhook.dss.server;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;

public class QueryStore extends SqlStore
{
	// note: these are sql-safe strings
	public final static String DSS_CONNECTION_NAME = "__dss__";
	public final static String DSS_CONNECTION_DESCRIPTION = "DSS Metadata Store";

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public QueryStore(SqlStore.Config cfg) throws Exception {
		super(cfg);
		this.ensuredSchema = false;
	}

	// +-----------------+
	// | listConnections |
	// +-----------------+

	// List all connections for which user has access

	public static class ListConnectionsItem
	{
		public String Name;
		public String Description;
		public Boolean IsEditor;

		static public ListConnectionsItem fromRow(ResultSet rs) throws SQLException {
			ListConnectionsItem item = new ListConnectionsItem();
			item.Name = rs.getString("name");
			item.Description = rs.getString("description");
			item.IsEditor = (rs.getInt("is_editor") != 0);
			return(item);
		}
	}
	
	public final static String LIST_CONNECTIONS_SQL =
		"select " + 
		"  c.name name, " +
		"  c.description description, " +
		"  a.is_editor is_editor " +
		"from " +
		"  connections c " +
		"inner join " +
		"  access a on c.name = a.connection_name and a.user = ? ";
		
	public List<ListConnectionsItem> listConnections(final String user)
		throws Exception {

		ensureSchema(user);
		
		List<ListConnectionsItem> items = new ArrayList<ListConnectionsItem>();
		
		query(LIST_CONNECTIONS_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				ListConnectionsItem item = ListConnectionsItem.fromRow(rs);
				items.add(item);
			}
		});

		return(items);
	}
	
	// +-------------+
	// | listQueries |
	// +-------------+

	// List all queries in a connection (checking access)
	
	public final static String LIST_QUERIES_SQL =
		"select " +
		"  q.id query_id, " +
		"  q.description description, " +
		"  q.owner owner " +
		"from " +
		"  queries q " +
		"inner join " +
		"  access a on q.connection_name = a.connection_name and a.user = ? " +
		"where " +
		"  q.connection_name = ? ";

	public static class ListQueriesItem
	{
		public UUID Id;
		public String Description;
		public String Owner;

		static public ListQueriesItem fromRow(ResultSet rs) throws SQLException {
			ListQueriesItem item = new ListQueriesItem();
			item.Id = UUID.fromString(rs.getString("query_id"));
			item.Description = rs.getString("description");
			item.Owner = rs.getString("owner");
			return(item);
		}
	}

	public List<ListQueriesItem> listQueries(final String connection, final String user)
		throws Exception {

		ensureSchema(user);
		
		List<ListQueriesItem> items = new ArrayList<ListQueriesItem>();
		
		query(LIST_QUERIES_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, connection);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				ListQueriesItem item = ListQueriesItem.fromRow(rs);
				items.add(item);
			}
		});

		return(items);
	}

	// +----------+
	// | getQuery |
	// +----------+

	public static enum ParamType
	{
		string,
		integer,
		date,
		datetime
	}
	
	public static class Param
	{
		public String Name;
		public ParamType Type;
		public String Default;
	}
	
	public static class Statement
	{
		public String Sql;
		public Param[] Params;
	}
	
	public static class Query
	{
		public UUID Id;
		public String Description;
		public String Owner;
		public Boolean IsEditor;
		public String ConnectionString;
		public Statement Statement;

		public String toJson() {
			return(new Gson().toJson(this));
		}
		
		public static Query fromRow(ResultSet rs, boolean forExecute)
			throws SQLException{

			Query q = new Query();
			
			q.Id = UUID.fromString(rs.getString("query_id"));
			q.Description = rs.getString("description");
			q.Owner = rs.getString("owner");
			q.IsEditor = (rs.getInt("is_editor") != 0);

			if (forExecute) {
				q.ConnectionString = rs.getString("connection_string");
			}
			
			if (forExecute || q.IsEditor) {
				String json = rs.getString("statement_json");
				q.Statement = new Gson().fromJson(json, Statement.class);
			}

			return(q);
		}
	}

	public final static String GET_QUERY_SQL =
		"select " +
		"  q.id query_id, " +
		"  q.description description, " +
		"  q.owner owner, " +
		"  a.is_editor is_editor, " +
		"  c.connection_string connection_string, " +
		"  q.statement_json statement_json " +
		"from " +
		"  queries q " +
		"inner join " +
		"  connections c on q.connection_name = c.connection_name " +
		"inner join " +
		"  access a on q.connection_name = a.connection_name and a.user = ? " +
		"where " +
		"  q.id = ? ";

	public Query getQuery(final UUID id, final String user, final boolean forExecute)
		throws Exception {

		ensureSchema(user);
		
		SqlStore.Return<Query> query = new SqlStore.Return<Query>();
		
		query(GET_QUERY_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, id.toString());
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				if (irow > 0) throw new Exception("gQI mult rows: " + id.toString());
				query.Value = Query.fromRow(rs, forExecute);
			}
		});

		return(query.Value);
	}

	// +--------------+
	// | ensureSchema |
	// +--------------+

	private void ensureSchema(String user) throws Exception {

		if (ensuredSchema) return;

		try {
			ensureTable("connections", CREATE_CONNECTIONS_TABLE);
			ensureTable("access", CREATE_ACCESS_TABLE);
			ensureTable("queries", CREATE_QUERIES_TABLE);
			
			ensureAdminConnection();
			ensureFirstAdminUser(user);
		}
		finally {
			// for better or worse!
			ensuredSchema = true;
		}
	}
	
	// need DSS represented in the connections list

	private void ensureAdminConnection() throws Exception {

		String connectionString = lookupAdminConnectionString();

		if (connectionString == null) {
			insertAdminConnectionString();
		}
		else if (!cfg.ConnectionString.equals(connectionString)) {
			repairAdminConnectionString();
		}
	}

	private String lookupAdminConnectionString() throws Exception {

		String sql = "select connection_string from connections where name = ?";
		SqlStore.Return<String> connectionString = new SqlStore.Return<String>();

		query(sql, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, DSS_CONNECTION_NAME);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				connectionString.Value = rs.getString("connection_string");
			}
		});

		return(connectionString.Value);
	}

	private void insertAdminConnectionString() throws Exception {

		log.info("ensureSchema: inserting DSS Admin Connection String");
		
		String sql =
			"insert into connections " +
			"(name, description, connection_string) values(?,?,?)";

		update(sql, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, DSS_CONNECTION_NAME);
				stmt.setString(2, DSS_CONNECTION_DESCRIPTION);
				stmt.setString(3, cfg.ConnectionString);
			}
		});
	}

	private void repairAdminConnectionString() throws Exception {

		log.info("ensureSchema: repairing DSS Admin Connection String");

		String sql =
			"update connections set connection_string = ? where name = ?";

		update(sql, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, cfg.ConnectionString);
				stmt.setString(2, DSS_CONNECTION_NAME);
			}
		});
	}

	// need at least 1 admin user
	
	private void ensureFirstAdminUser(String user) throws Exception {

		String adminUser = lookupFirstAdminUser();
		if (adminUser == null) insertFirstAdminUser(user);
	}

	private String lookupFirstAdminUser() throws Exception {

		String sql =
			"select user from access " +
			"where connection_name = ? and is_editor = 1 " +
			"limit 1";
		
		SqlStore.Return<String> user = new SqlStore.Return<String>();

		query(sql, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, DSS_CONNECTION_NAME);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				user.Value = rs.getString("user");
			}
		});

		return(user.Value);
	}

	private void insertFirstAdminUser(String user) throws Exception {
		
		log.info("ensureSchema: inserting first admin user (" + user + ")");

		String sql =
			"insert into access " +
			"(connection_name, user, is_editor) values(?,?,?)";

		update(sql, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, DSS_CONNECTION_NAME);
				stmt.setString(2, user);
				stmt.setInt(3, 1);
			}
		});
	}

	// +--------+
	// | Schema |
	// +--------+

	private final static String CREATE_CONNECTIONS_TABLE =
		"create table connections " +
		"( " +
		"    name varchar(64) not null, " +
		"    description varchar(256) not null, " +
		"    connection_string varchar(256) not null, " +
		"    note varchar(256) null, " +
		" " +
		"    primary key (name) " +
		") ";
	
	private final static String CREATE_ACCESS_TABLE =
		"create table access " +
		"( " +
		"    connection_name varchar(64) not null, " +
		"    user varchar(128) not null, " +
		"    is_editor boolean null, " +
		"    note varchar(256) null, " +
		" " +
		"    primary key (connection_name, user) " +
		") ";

	private final static String CREATE_QUERIES_TABLE =
		"create table queries " +
		"( " +
		"    id char(36) not null, " +
		"    connection_name varchar(64) not null, " +
		"    description varchar(64) not null, " +
		"    statement_json text not null, " +
		"    owner varchar(128) not null, " +
		"    note varchar(256) null, " +
		" " +
		"    primary key (id) " +
		") ";

	// +---------+
	// | Members |
	// +---------+

	private boolean ensuredSchema;
	
	private final static Logger log = Logger.getLogger(QueryStore.class.getName());
}
