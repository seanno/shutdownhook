/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.BeforeClass;
import org.junit.AfterClass;

public class SqlTest
{
	private static String SQLITE_TEMP_FILE =
		"/tmp/xyz" + Integer.toString(new Random().nextInt(20000));
		
	private static String SQLITE_CXN_STRING =
		"jdbc:sqlite:" + SQLITE_TEMP_FILE;

	private static SqlStore sql;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		sql = new SqlStore(new SqlStore.Config(SQLITE_CXN_STRING));
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		sql = null;
		(new File(SQLITE_TEMP_FILE)).delete();
	}

	@Test
	public void testRoundTrip() throws Exception {

		String createSql = "create table xyz ( foo int primary key, bar varchar(32) )";
		String dropSql = "drop table xyz";
		String insertSql = "insert into xyz (foo, bar) values (?,?)";
		String querySql = "select bar from xyz where foo = ?";
		
		int[] keys = new int[] { 1, 100, -5, 160000 };
		String[] vals = new String[] { "yo", "", "banana", "zippy\n\roink" };
		
		sql.execute(createSql);

		sql.update(insertSql, new SqlStore.UpdateHandler() {
			public boolean proceed(int iter) {
				return(iter < keys.length);
			}
			public void prepare(PreparedStatement stmt, int iter) throws Exception {
				stmt.setInt(1, keys[iter]);
				stmt.setString(2, vals[iter]);
			}
			public void confirm(int rowsAffected, int iter) {
				Assert.assertEquals(1, rowsAffected);
			}
		});

		sql.query(querySql, new SqlStore.QueryHandler() {
			public void prepare(PreparedStatement stmt) throws Exception {
				stmt.setInt(1, keys[0]);
			}
			public void row(ResultSet rs, int irow) throws Exception {
				String val = rs.getString(1);
				Assert.assertEquals(irow, 0);
				Assert.assertEquals(vals[0], val);
			}
		});
		
		sql.execute(dropSql);
	}
}
