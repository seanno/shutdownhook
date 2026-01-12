//
// RESOURCE.JAVA
//

package com.shutdownhook.backstop;

import java.util.ArrayList;
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
		public String Link;
		public String StateId;
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
	
	public static class Status
	{
		public Status(String metric, StatusLevel level, String result, String link) {
			this.metric = metric;
			this.level = level;
			this.result = result;
			this.link = link;
		}

		public Status(String metric, StatusLevel level, String result) {
			this(metric, level, result, null);
		}

		public Status(String metric, StatusLevel level) {
			this(metric, level, null);
		}

		public String getMetric() { return(metric); }
		public StatusLevel getLevel() { return(level); }
		public String getResult() { return(result == null ? "" : result); }
		public String getLink() { return(link == null ? "" : link); }

		private String metric;
		private StatusLevel level;
		private String result;
		private String link;
	}

	// +-------+
	// | check |
	// +-------+

	public interface Checker {
		public void check(Map<String,String> params,
						  BackstopHelpers helpers,
						  String stateId,
						  List<Status> statuses) throws Exception;
	}

	public static List<Status> check(Config cfg, BackstopHelpers helpers) {

		List<Status> statuses = new ArrayList<Status>();
		
		try {
			checkInternal(cfg, helpers, statuses);
			if (statuses.size() == 0) statuses.add(new Status("", StatusLevel.OK));
		}
		catch (Exception e) {
			
			statuses.add(new Status("Exception", StatusLevel.ERROR,
									e.getClass().getName() + ": " + e.getMessage()));

			System.err.println(Easy.exMsg(e, "ex", true));
		}

		return(statuses);
	}
	
	private static void checkInternal(Config cfg,
									  BackstopHelpers helpers,
									  List<Status> statuses) throws Exception {

		if (Easy.nullOrEmpty(cfg.Class)) throw new Exception("Missing resource Class field");
		Class cls = Class.forName(cfg.Class);

		if (!Checker.class.isAssignableFrom(cls)) {
			throw new Exception(cfg.Class + " must implement Checker interface");
		}

		Checker checker = (Checker) cls.getDeclaredConstructor().newInstance();
		checker.check(getParameters(cfg, helpers), helpers, cfg.StateId, statuses);
	}

	private static Map<String,String> getParameters(Config cfg, BackstopHelpers helpers) throws Exception {

		if (cfg.StateId == null) return(cfg.Parameters);

		Map<String,String> params = new HashMap<String,String>();
		params.putAll(cfg.Parameters);
		params.putAll(helpers.getAllState(cfg.StateId));

		return(params);
	}

	// +-------------------+
	// | DescartesResource |
	// +-------------------+

	public static class DescartesResource implements Checker {
		
		public void check(Map<String,String> parameters,
						  BackstopHelpers helpers,
						  String stateId,
						  List<Status> statuses) throws Exception {
			
			statuses.add(new Status("", StatusLevel.OK, "I ran, therefore I am."));
		}
	}

}
