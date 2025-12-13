//
// RESOURCE.JAVA
//

package com.shutdownhook.backstop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.shutdownhook.toolbox.Easy;

public class Resource
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String Name;
		public String Class;
		public Map<String,String> Parameters = new HashMap<String,String>();
	}
		
	// +--------+
	// | Status |
	// +--------+

	public static enum StatusLevel
	{
		OK,
		WARNING,
		ERROR;
	}
	
	public static class Status implements Comparable<Status>
	{
		public String Resource;
		public String Metric;
		public StatusLevel Level;
		public String Result;

		public Status(Config cfg) {
			this.Resource = cfg.Name;
			this.Level = StatusLevel.OK;
		}
		
		public int compareTo(Status other) {
			if (other == null) throw new NullPointerException();
			int cmp = other.Level.ordinal() - this.Level.ordinal();
			if (cmp == 0) cmp = this.Resource.compareTo(other.Resource);
			if (cmp == 0) cmp = this.Metric.compareTo(other.Metric);
			return(cmp);
		}
	}

	// +-------+
	// | check |
	// +-------+

	public interface Checker {
		public void check(Config cfg, List<Status> statuses) throws Exception;
	}

	public static void check(Config cfg, List<Status> statuses) {

		try {
			checkInternal(cfg, statuses);
		}
		catch (Exception e) {
			
			Status exStatus = new Resource.Status(cfg);
			exStatus.Level = StatusLevel.ERROR;
			exStatus.Result = e.getClass().getName() + ": " + e.getMessage();
			statuses.add(exStatus);

			System.err.println(Easy.exMsg(e, "ex", true));
		}
	}
	
	private static void checkInternal(Config cfg, List<Status> statuses) throws Exception {

		if (Easy.nullOrEmpty(cfg.Name)) throw new Exception("Missing resource Name field");
		if (Easy.nullOrEmpty(cfg.Class)) throw new Exception("Missing resource Class field");
		
		Class cls = Class.forName(cfg.Class);

		if (!Checker.class.isAssignableFrom(cls)) {
			throw new Exception(cfg.Class + " must implement Checker interface");
		}

		Checker checker = (Checker) cls.getDeclaredConstructor().newInstance();
		checker.check(cfg, statuses);
	}

	// +-------------------+
	// | DescartesResource |
	// +-------------------+

	public static class DescartesResource implements Checker {
		public void check(Config cfg, List<Status> statuses) throws Exception {
			Status status = new Status(cfg);
			status.Result = "I ran, therefore I am.";
			statuses.add(status);
		}
	}

}
