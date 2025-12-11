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
		public String Name;
		public StatusLevel Level;
		public double Numeric;
		public String Text;
		public String Narrative;

		public Status(String name, StatusLevel level, double numeric,
					  String text, String narrative) {

			this.Name = name;
			this.Level = level;
			this.Numeric = numeric;
			this.Text = text;
			this.Narrative = narrative;
		}

		public Status(Config cfg) {
			this.Name = cfg.Name;
			this.Level = StatusLevel.OK;
		}

		public String getResultText(int decimalPlaces) {
			if (Text != null) return(Text);
			return(String.format("%.3f", Numeric));
		}

		public int compareTo(Status other) {
			if (other == null) throw new NullPointerException();
			int cmp = this.Level.ordinal() - other.Level.ordinal();
			if (cmp == 0) cmp = this.Name.compareTo(other.Name);
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
			exStatus.Text = e.getMessage();
			exStatus.Narrative = e.toString();

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

	// +------------------+
	// | DescartesChecker |
	// +------------------+

	public static class DescartesChecker implements Checker {
		public void check(Config cfg, List<Status> statuses) throws Exception {
			Status status = new Status(cfg);
			status.Text = "I ran, therefore I am.";
			statuses.add(status);
		}
	}

}
