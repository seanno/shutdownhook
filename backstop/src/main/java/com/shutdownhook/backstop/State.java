//
// STATE.JAVA
//

package com.shutdownhook.backstop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.shutdownhook.toolbox.SqlStore;

public class State
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public State(SqlStore.Config cfg) throws Exception {
		this.sql = new SqlStore(cfg);
		this.ensureTables();
	}

	// +--------------+
	// | get / getAll |
	// +--------------+

	private final static String GET_STATE =
		"select value from state where id = ? and name = ?";

	private final static String GET_ALL_STATE =
		"select name, value from state where id = ?";
		
	public String get(final String id, final String name) throws Exception {

		List<String> vals = new ArrayList<String>();
		
		sql.query(GET_STATE, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, id);
				stmt.setString(2, name);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				vals.add(rs.getString(1));
			}
		});

		if (vals.size() > 1) throw new Exception("PK violation in state table");
		return(vals.size() == 0 ? null : vals.get(0));
	}

	public Map<String,String> getAll(final String id) throws Exception {

		Map<String,String> vals = new HashMap<String,String>();
		
		sql.query(GET_ALL_STATE, new SqlStore.QueryHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, id);
			}
				
			public void row(ResultSet rs, int irow) throws Exception {
				vals.put(rs.getString(1), rs.getString(2));
			}
		});

		return(vals);
	}
	
	// +-----+
	// | set |
	// +-----+

	private final static String UPDATE_STATE =
		"update state set value = ?, epoch_second = ? where id = ? and name = ?";

	private final static String INSERT_STATE =
		"insert into state (value, epoch_second, id, name) values(?,?,?,?)";

	public void set(String id, String name, String value) throws Exception {
		long now = Instant.now().getEpochSecond();
		boolean updated = setUpdate(id, name, value, now);
		if (!updated) setInsert(id, name, value, now);
	}

	private void setInsert(String id, String name, String value, long epochSecond) throws Exception {
		
		sql.execute(INSERT_STATE, new SqlStore.ExecuteHandler() {
				
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setString(1, value);
				stmt.setLong(2, epochSecond);
				stmt.setString(3, id);
				stmt.setString(4, name);
			}
		});
	}

	private boolean setUpdate(String id, String name, String value, long epochSecond) throws Exception {
		
		SqlStore.Return<Boolean> updated = new SqlStore.Return<Boolean>();
		updated.Value = false;
			
		sql.update(UPDATE_STATE, new SqlStore.UpdateHandler() {
				
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setString(1, value);
				stmt.setLong(2, epochSecond);
				stmt.setString(3, id);
				stmt.setString(4, name);
			}
				
			public void confirm(int rowsAffected, int iter) {
				updated.Value = (rowsAffected == 1);
			}
		});

		return(updated.Value);
	}

	// +-----+
	// | DDL |
	// +-----+

	private final static String CREATE_STATE_TABLE =
		"create table state " +
		"( " +
		"    id varchar(64) not null, " +
		"    name varchar(64) not null, " +
		"    value varchar not null, " +
		"    epoch_second integer not null, " +
		" " +
		"    primary key (id, name) " +
		") ";

	private void ensureTables() throws Exception {
		sql.ensureTable("state", CREATE_STATE_TABLE);
	}
	
	// +---------+
	// | Members |
	// +---------+

	public SqlStore sql;
}
