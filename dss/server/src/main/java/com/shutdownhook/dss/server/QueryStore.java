/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

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
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public final static String DSS_CONNECTION_NAME = "__dss__";
	public final static String DSS_CONNECTION_DESCRIPTION = "DSS Metadata Store";

	public QueryStore(SqlStore.Config cfg) throws Exception {
		super(cfg);
		this.ensuredSchema = false;
	}

	// +---------------+
	// | my_access CTE |
	// +---------------+

	// consolidates possible multiple access matches into a single
	// row with the maximal values for can_create (i.e., true if any true)
	
	public final static String MY_ACCESS_CTE =
		"with my_access as ( " +
		"  select connection_name, max(can_create) can_create " +
		"  from access " +
		"  where ? like user " +
		"  group by connection_name " +
		") ";

	// +-------------+
	// | listQueries |
	// +-------------+

	// Lists all connections the user has access to, including a list
	// of existing queries for each connection that the user can run
	
	public static class QueryListItem
	{
		public UUID Id;
		public String Description;
		public String Owner;
		public Boolean IsShared;
	}

	public static class ConnectionListItem
	{
		public String Name;
		public String Description;
		public Boolean CanCreate;
		public List<QueryListItem> Queries = new ArrayList<QueryListItem>();
	}

	public static class QueryTree
	{
		public String User;
		public List<ConnectionListItem> Connections = new ArrayList<ConnectionListItem>();
	}

	private final static String LIST_QUERIES_SQL =
		MY_ACCESS_CTE +
		"select " +
		"  c.name connection_name, " +
		"  c.description connection_description, " +
		"  a.can_create can_create, " +
		"  q.id query_id, " +
		"  q.description query_description, " +
		"  q.owner owner, " +
		"  q.is_shared is_shared " +
		"from " +
		"  connections c " +
		"inner join " +
		"  my_access a on c.name = a.connection_name " +
		"left outer join " +
		"  queries q on c.name = q.connection_name and (q.is_shared or q.owner = ?) " +
		"order by " +
		"  c.name, c.description, q.description ";

	public QueryTree listQueries(String user) throws Exception {

		ensureSchema(user);

		QueryTree queryTree = new QueryTree();
		queryTree.User = user;
		
		query(LIST_QUERIES_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, user);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				
				String connectionName = rs.getString("connection_name");
				if (c == null || !c.Name.equals(connectionName)) {
					c = new ConnectionListItem();
					queryTree.Connections.add(c);
					c.Name = connectionName;
					c.Description = rs.getString("connection_description");
					c.CanCreate = (rs.getInt("can_create") != 0);
				}

				String idStr = rs.getString("query_id");
				if (idStr != null) { 
					QueryListItem q = new QueryListItem();
					c.Queries.add(q);
					q.Id = UUID.fromString(idStr);
					q.Description = rs.getString("query_description");
					q.Owner = rs.getString("owner");
					q.IsShared = (rs.getInt("is_shared") != 0);
				}
			}

			private ConnectionListItem c = null;
		});

		return(queryTree);
	}

	// +------------------------------+
	// | getConnectionExecutionInfo   |
	// | canCreateInConnection        |
	// +------------------------------+

	// returns connection string if user is allowed to create or run ad hoc
	// sql in the given connection
	
	final static String GET_CONNECTION_EXECUTION_INFO_SQL =
		MY_ACCESS_CTE +
		"select " +
		"  c.connection_string connection_string, " +
		"  c.log_queries log_queries " +
		"from " +
		"  connections c " +
		"inner join " +
		"  my_access a on c.name = a.connection_name and a.can_create = 1 " +
		"where " +
		"  c.name = ? ";

	public ExecutionInfo getConnectionExecutionInfo(String connectionName, String user)
		throws Exception {

		ensureSchema(user);

		SqlStore.Return<ExecutionInfo> info = new SqlStore.Return<ExecutionInfo>();

		query(GET_CONNECTION_EXECUTION_INFO_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, connectionName);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				info.Value = new ExecutionInfo();
				info.Value.ConnectionString = rs.getString("connection_string");
				info.Value.LogQueries = (rs.getInt("log_queries") != 0);
			}
		});

		return(info.Value);
	}

	public boolean canCreateInConnection(String connectionName, String user) throws Exception {
		ExecutionInfo info = getConnectionExecutionInfo(connectionName, user);
		return(info != null);
	}

	// +-----------------------+
	// | getQueryExecutionInfo |
	// +-----------------------+

	// returns information needed to execute an existing query to which user has access
	
	final static String GET_QUERY_EXECUTION_INFO_SQL =
		MY_ACCESS_CTE +
		"select " +
		"  c.connection_string connection_string, " +
		"  c.log_queries log_queries, " + 
		"  q.statement statement " +
		"from " +
		"  queries q " +
		"inner join " +
		"  connections c on q.connection_name = c.name " +
		"inner join " +
		"  my_access a on q.connection_name = a.connection_name " +
		"where " +
		"  q.id = ? and " +
		"  (q.owner = ? or q.is_shared = 1) ";

	public static class ExecutionInfo
	{
		public String ConnectionString;
		public String Statement;
		public Boolean LogQueries;
	}

	public ExecutionInfo getQueryExecutionInfo(UUID queryId, String user) throws Exception {

		ensureSchema(user);
		
		SqlStore.Return<ExecutionInfo> info = new SqlStore.Return<ExecutionInfo>();

		query(GET_QUERY_EXECUTION_INFO_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, queryId.toString());
				stmt.setString(3, user);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				info.Value = new ExecutionInfo();
				info.Value.ConnectionString = rs.getString("connection_string");
				info.Value.Statement = rs.getString("statement");
				info.Value.LogQueries = (rs.getInt("log_queries") != 0);
			}
		});

		return(info.Value);
	}

	// +-----------------+
	// | getQueryDetails |
	// +-----------------+

	// fetches details about a query that the user has access to.
	// if the user is the owner, the statement is also returned.

	public static class QueryParam
	{
		public String Name;
		public String Default;
	}
	
	public static class QueryDetails
	{
		public UUID Id;
		public String ConnectionName;
		public String Description;
		public String Owner;
		public Boolean IsShared;
		public String ParamsCsv;
		public String Statement;

		public static QueryDetails fromJson(String json) {
			return(new Gson().fromJson(json, QueryDetails.class));
		}
	}

	private final static String QUERY_DETAILS_SQL =
		MY_ACCESS_CTE +
		"select " +
		"  q.id id, " +
		"  q.connection_name connection_name, " +
		"  q.description description, " +
		"  q.owner owner, " +
		"  q.is_shared is_shared, " +
		"  q.params_csv params_csv, " +
		"  case when (q.owner = ?) then q.statement else null end statement " +
		"from " +
		"  queries q " +
		"inner join " +
		"  my_access a on q.connection_name = a.connection_name " +
		"where " +
		"  (q.id = ?) and (q.is_shared or q.owner = ?)";

	public QueryDetails getQueryDetails(UUID id, String user) throws Exception {

		ensureSchema(user);

		SqlStore.Return<QueryDetails> details = new SqlStore.Return<QueryDetails>();
			
		query(QUERY_DETAILS_SQL, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, user);
				stmt.setString(2, user);
				stmt.setString(3, id.toString());
				stmt.setString(4, user);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {

				QueryDetails qd = new QueryDetails();
				details.Value = qd;

				qd.Id = UUID.fromString(rs.getString("id"));
				qd.Description = rs.getString("description");
				qd.ConnectionName = rs.getString("connection_name");
				qd.Owner = rs.getString("owner");
				qd.IsShared = (rs.getInt("is_shared") != 0);
				qd.Statement = rs.getString("statement");
				qd.ParamsCsv = rs.getString("params_csv");
			}
		});

		return(details.Value);
	}
																	 
	// +-------------+
	// | deleteQuery |
	// +-------------+

	final static String DELETE_QUERY_SQL =
		"delete from queries where id = ? and owner = ?";

	public boolean deleteQuery(UUID id, String user) throws Exception {

		ensureSchema(user);

		SqlStore.Return<Boolean> success = new SqlStore.Return<Boolean>();
		success.Value = false;
			
		update(DELETE_QUERY_SQL, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, id.toString());
				stmt.setString(2, user);
			}
				
			public void confirm(int rowsAffected, int iter) {
				success.Value = (rowsAffected == 1);
			}
		});

		return(success.Value);
	}

	// +-----------+
	// | saveQuery |
	// +-----------+

	// inserts or updates a query. User must have create access in the connection to
	// insert; must be owner to update. connection/owner are never changed on update
	
	final static String UPDATE_QUERY_SQL =
		"update queries " +
		"set description = ?, is_shared = ?, params_csv = ?, statement = ? " +
		"where id = ? and owner = ? ";

	final static String INSERT_QUERY_SQL =
		"insert into queries " +
		"(id, connection_name, owner, description, is_shared, params_csv, statement) " +
		"values (?,?,?,?,?,?,?) ";

	public UUID saveQuery(QueryDetails details, String user) throws Exception {

		ensureSchema(user);

		if (details.Id != null) {
			return(updateQuery(details, user));
		}

		if (!canCreateInConnection(details.ConnectionName, user)) {
			throw new Exception("saveQuery: can't create in connection");
		}

		details.Id = UUID.randomUUID();
		details.Owner = user;

		return(insertQuery(details));
	}

	private UUID updateQuery(QueryDetails details, String user) throws Exception {

		SqlStore.Return<Boolean> ok = new SqlStore.Return<Boolean>();
		ok.Value = false;
		
		update(UPDATE_QUERY_SQL, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, details.Description);
				stmt.setInt(2, details.IsShared ? 1 : 0);
				stmt.setString(3, details.ParamsCsv);
				stmt.setString(4, details.Statement);
				stmt.setString(5, details.Id.toString());
				stmt.setString(6, user);
			}
				
			public void confirm(int rowsAffected, int iter) {
				ok.Value = true;
			}
		});

		return(ok.Value ? details.Id : null);
	}

	private UUID insertQuery(QueryDetails details) throws Exception {

		SqlStore.Return<Boolean> ok = new SqlStore.Return<Boolean>();
		ok.Value = false;
		
		update(INSERT_QUERY_SQL, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, details.Id.toString());
				stmt.setString(2, details.ConnectionName);
				stmt.setString(3, details.Owner);
				stmt.setString(4, details.Description);
				stmt.setInt(5, details.IsShared ? 1 : 0);
				stmt.setString(6, details.ParamsCsv);
				stmt.setString(7, details.Statement);
			}
				
			public void confirm(int rowsAffected, int iter) {
				ok.Value = true;
			}
		});

		return(ok.Value ? details.Id : null);
	}
	
	// +--------------+
	// | ensureSchema |
	// +--------------+

	private void ensureSchema(String user) throws Exception {

		if (ensuredSchema) return;

		try {
			boolean creating = !tableExists("connections");
			
			ensureTable("connections", CREATE_CONNECTIONS_TABLE);
			ensureTable("access", CREATE_ACCESS_TABLE);
			ensureTable("queries", CREATE_QUERIES_TABLE);
			
			ensureAdminConnection();
			ensureFirstAdminUser(user);

			if (creating) insertAdminQueries(user);
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
			"(name, description, connection_string,log_queries) values(?,?,?,0)";

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
			"where connection_name = ? and can_create = 1 " +
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
			"(connection_name, user, can_create) values(?,?,?)";

		update(sql, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, DSS_CONNECTION_NAME);
				stmt.setString(2, user);
				stmt.setInt(3, 1);
			}
		});
	}

	// insert some useful queries against the DSS connection

	public static class AdminQueryInfo
	{
		public String description;
		public String statement;
		public String parameters;
		public Integer is_shared;
	}

	private final static String INSERT_ADMIN_QUERY_SQL =
		"insert into queries " +
		"(id, connection_name, description, statement, params_csv, owner, is_shared) " +
		"values(?,?,?,?,?,?,?) ";
	
	private void insertAdminQueries(String user) throws Exception {

		String queriesJson = Easy.stringFromResource("dssDML.json");
		AdminQueryInfo[] queries = new Gson().fromJson(queriesJson, AdminQueryInfo[].class);

		log.info(String.format("Inserting %d admin queries", queries.length));
		
		update(INSERT_ADMIN_QUERY_SQL, new SqlStore.UpdateHandler() {
				
			public boolean proceed(int iter) { 
				return(iter < queries.length);
			}
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				AdminQueryInfo info = queries[iter];
				stmt.setString(1, UUID.randomUUID().toString());
				stmt.setString(2, DSS_CONNECTION_NAME);
				stmt.setString(3, info.description);
				stmt.setString(4, info.statement);
				stmt.setString(5, info.parameters);
				stmt.setString(6, user);
				stmt.setInt(7, info.is_shared);
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
		"    log_queries boolean null, " +
		"    note varchar(256) null, " +
		" " +
		"    primary key (name) " +
		") ";
	
	private final static String CREATE_ACCESS_TABLE =
		"create table access " +
		"( " +
		"    connection_name varchar(64) not null, " +
		"    user varchar(128) not null, " +
		"    can_create boolean null, " +
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
		"    statement text not null, " +
		"    params_csv text null, " +
		"    owner varchar(128) not null, " +
		"    is_shared boolean null, " +
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
