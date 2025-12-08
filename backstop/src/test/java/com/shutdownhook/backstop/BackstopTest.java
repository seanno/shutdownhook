/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.backstop;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class BackstopTest
{
	// +-------+
	// | Tests |
	// +-------+

	@Test
	public void testBasic() throws Exception {
		testOne("basicTestConfig", 1);
		testOne("exceptionTestConfig", 1);
		testOne("complexTestConfig", 10);
	}

	private void testOne(String name, int threads) throws Exception {
		Backstop backstop = getBackstop(name, threads);
		List<Status> results = backstop.checkAll();
		TestResource.assertResults(results, backstop.getConfig().Resources);
	}

	// +---------+
	// | Helpers |
	// +---------+

	public static Backstop getBackstop(String name, int threads) throws Exception {

		Backstop.Config backstopCfg = new Backstop.Config();
		backstopCfg.Threads = threads;

		String json = Easy.stringFromResource(name + ".json");		
		backstopCfg.Resources = TestResource.resourceConfigsFromJsonArray(json);

		return(new Backstop(backstopCfg));
	}
}

