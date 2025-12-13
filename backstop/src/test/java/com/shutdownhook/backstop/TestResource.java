/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.backstop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class TestResource implements Checker
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public String Name;
		public int SleepMillis = 0;
		public String ExceptionMessage = null;
		
		public Resource.Status[] Statuses;

		public String toJson() {
			return(new Gson().toJson(this));
		}
		
		public static Config fromJson(String json) throws JsonSyntaxException {
			return(new Gson().fromJson(json, TestResource.Config.class));
		}
		
		public static Config[] fromJsonArray(String json) throws JsonSyntaxException {
			return(new Gson().fromJson(json, TestResource.Config[].class));
		}
	}
	
	// +---------------+
	// | Checker.check |
	// +---------------+
	
	public void check(Resource.Config resourceCfg, List<Status> statuses) throws Exception {

		String json = resourceCfg.Parameters.get("cfg");
		Config testCfg = new Gson().fromJson(json, Config.class);

		if (testCfg.SleepMillis > 0) Thread.sleep(testCfg.SleepMillis);

		if (testCfg.ExceptionMessage != null) {
			throw new Exception(testCfg.ExceptionMessage);
		}
		
		if (testCfg.Statuses == null) return;
		
		for (Resource.Status testStatus : testCfg.Statuses) {

			Resource.Status thisStatus = new Resource.Status(resourceCfg);

			thisStatus.Metric = testStatus.Metric;
			thisStatus.Level = testStatus.Level;
			thisStatus.Result = testStatus.Result;

			statuses.add(thisStatus);
		}
	}
	
	// +---------------+
	// | assertResults |
	// +---------------+

	public static void assertResults(List<Resource.Status> results,
									 Resource.Config[] resources) throws Exception {

		List<Resource.Status> expected = getExpectedResults(resources);

		Assert.assertEquals(expected.size(), results.size());

		for (int i = 0; i < expected.size(); ++i) {
			Resource.Status e = expected.get(i);
			Resource.Status a = results.get(i);
			Assert.assertEquals(e.Resource, a.Resource);
			Assert.assertEquals(e.Metric, a.Metric);
			Assert.assertEquals(e.Level, a.Level);
			Assert.assertEquals(e.Result, a.Result);
		}
		
	}

	public static List<Resource.Status> getExpectedResults(Resource.Config[] resources) {

		List<Resource.Status> expected = new ArrayList<Resource.Status>();

		for (Resource.Config resourceCfg : resources) {
			
			String json = resourceCfg.Parameters.get("cfg");
			Config testCfg = new Gson().fromJson(json, Config.class);

			if (testCfg.ExceptionMessage != null) {
				Status s = new Resource.Status(resourceCfg);
				s.Level = Resource.StatusLevel.ERROR;
				s.Result = "java.lang.Exception: " + testCfg.ExceptionMessage;
				expected.add(s);
			}
			else if (testCfg.Statuses == null || testCfg.Statuses.length == 0) {
				expected.add(new Resource.Status(resourceCfg));
			}
			else {
				for (Resource.Status testStatus : testCfg.Statuses) {

					Status s = new Resource.Status(resourceCfg);
					s.Metric = testStatus.Metric;
					s.Level = testStatus.Level;
					s.Result = testStatus.Result;

					expected.add(s);
				}
			}
		}

		Collections.sort(expected);
		return(expected);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	public static Resource.Config[]
		resourceConfigsFromJsonArray(String jsonArray) throws Exception {

		Config[] testConfigs = Config.fromJsonArray(jsonArray);

		Resource.Config[] resourceConfigs = new Resource.Config[testConfigs.length];

		for (int i = 0; i < resourceConfigs.length; ++i) {
			
			resourceConfigs[i] = new Resource.Config();
			resourceConfigs[i].Name = testConfigs[i].Name;
			resourceConfigs[i].Class = "com.shutdownhook.backstop.TestResource";
			resourceConfigs[i].Parameters.put("cfg", testConfigs[i].toJson());
		}

		return(resourceConfigs);
	}
}
