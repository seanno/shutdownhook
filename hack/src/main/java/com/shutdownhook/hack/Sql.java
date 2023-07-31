/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.hack;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Sql
{
	public static void inject(String user, String password, boolean allowInjection)
		throws SQLException {

		Connection cxn = null;

		try {
			cxn = DriverManager.getConnection("jdbc:sqlite:/tmp/injection.sql");
			ensureData(cxn);
			
			String loggedInUser = allowInjection
				? loginBad(cxn, user, password)
				: loginGood(cxn, user, password);
			
			if (loggedInUser == null) System.out.println("Login failed.");
			else System.out.println("Logged in as user: " + loggedInUser);
		}
		finally {
			if (cxn != null) cxn.close();
		}
	}

	private static String loginBad(Connection cxn, String user, String password)
		throws SQLException {

		Statement stmt = null;
		ResultSet rs = null;

		String loggedInUser = null;
		
		try {
			String sql = String.format("select user from u " +
									   "where user = '%s' and pw = '%s'",
									   user, password);
			
			stmt = cxn.createStatement();
			rs = stmt.executeQuery(sql);
			if (rs != null && rs.next()) loggedInUser = rs.getString(1);
		}
		finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
		}

		return(loggedInUser);
	}

	private static String loginGood(Connection cxn, String user, String password)
		throws SQLException {

		PreparedStatement stmt = null;
		ResultSet rs = null;

		String loggedInUser = null;
		
		try {
			stmt = cxn.prepareStatement("select user from u where " +
										"user = ? and pw = ?");

			stmt.setString(1, user);
			stmt.setString(2, password);
			
			rs = stmt.executeQuery();
			if (rs != null && rs.next()) loggedInUser = rs.getString(1);
		}
		finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
		}

		return(loggedInUser);
	}

	private static void ensureData(Connection cxn)
		throws SQLException {

		if (tableExists(cxn, "u")) return;
		
		exec(cxn, "create table u (user varchar(64) primary key, pw varchar(64))");
		exec(cxn, "insert into u values('user1','pass1')");
		exec(cxn, "insert into u values('user2','pass2')");
	}

	private static boolean tableExists(Connection cxn, String name)
		throws SQLException {

		Statement stmt = null;
		ResultSet rs = null;
		boolean exists = false;
		
		try {
			stmt = cxn.createStatement();
			rs = stmt.executeQuery("select count(*) from " + name);
			if (rs != null && rs.next()) exists = true;
		}
		catch (SQLException e) {
			// nothing
		}
		finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
		}

		return(exists);
	}

	private static boolean exec(Connection cxn, String sql)
		throws SQLException {

		Statement stmt = null;

		try {
			stmt = cxn.createStatement();
			return(stmt.execute(sql));
		}
		finally {
			if (stmt != null) stmt.close();
		}
	}

}
