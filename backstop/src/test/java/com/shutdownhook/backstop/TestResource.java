/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.backstop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class TestResource implements Checker
{
	// +---------------+
	// | Checker.check |
	// +---------------+
	
	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  List<Status> statuses) throws Exception {

		String sleepMillis = params.get("SleepMillis");
		if (!Easy.nullOrEmpty(sleepMillis)) Thread.sleep(Integer.parseInt(sleepMillis));

		String exceptionMessage = params.get("ExceptionMessage");
		if (!Easy.nullOrEmpty(exceptionMessage)) {
			throw new Exception(exceptionMessage);
		}
		
		String statusesString = params.get("Statuses");
		if (!Easy.nullOrEmpty(statusesString)) {
			statuses.addAll(statusesFromString(statusesString));
		}
	}
	
	// +---------------+
	// | assertResults |
	// +---------------+

	public static void assertEquals(List<Status> expected,
									List<Status> actual) throws Exception {

		Assert.assertEquals(expected.size(), actual.size());

		for (int i = 0; i < expected.size(); ++i) {
			assertStatusesEqual(expected.get(i), actual.get(i));
		}
	}

	public static List<Status> getExpectedStatuses(Resource.Config resource) throws Exception {

		List<Status> expected = new ArrayList<Status>();
		
		String exMsg = resource.Parameters.get("ExceptionMessage");
		if (!Easy.nullOrEmpty(exMsg)) {
			expected.add(new Status("Exception", StatusLevel.ERROR,
									"java.lang.Exception: " + exMsg));
			return(expected);
		}

		String statusString = resource.Parameters.get("Statuses");
		expected.addAll(statusesFromString(statusString));

		if (expected.size() == 0) {
			expected.add(new Status("", StatusLevel.OK, ""));
		}

		return(expected);
	}

	public static void assertStatusesEqual(Status e, Status a) {
		Assert.assertEquals(e.getMetric(), a.getMetric());
		Assert.assertEquals(e.getLevel(), a.getLevel());
		Assert.assertEquals(e.getResult(), a.getResult());
		Assert.assertEquals(e.getLink(), a.getLink());
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static List<Status> statusesFromString(String input) throws Exception {

		List<Status> statuses = new ArrayList<Status>();
		if (Easy.nullOrEmpty(input)) return(statuses);

		for (String statusString : input.split("\\|")) {
			String[] fields = statusString.split("^");
			statuses.add(new Status(fields[0], StatusLevel.valueOf(fields[1]), fields[2], fields[3]));
		}

		return(statuses);
	}
}
