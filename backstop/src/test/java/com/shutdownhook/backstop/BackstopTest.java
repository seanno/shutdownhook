/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.backstop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.backstop.Backstop.BackstopStatus;
import com.shutdownhook.backstop.Resource.Status;

public class BackstopTest
{
	// +-------+
	// | Tests |
	// +-------+

	@Test
	public void testBasic() throws Exception {
		testOne("basicTestConfig");
		testOne("exceptionTestConfig");
		testOne("complexTestConfig");
	}

	private void testOne(String name) throws Exception {
		
		String json = Easy.stringFromResource(name + ".json");
		Backstop.Config cfg = Backstop.Config.fromJson(json);

		List<BackstopStatus> expectedStatuses = new ArrayList<BackstopStatus>();
		
		for (Resource.Config resourceConfig : cfg.Resources) {
			List<Status> expected = TestResource.getExpectedStatuses(resourceConfig);
			List<Status> actual = Resource.check(resourceConfig, null);
			TestResource.assertEquals(expected, actual);

			for (Status testStatus : actual) {
				String link = testStatus.getLink();
				if (Easy.nullOrEmpty(link)) link = resourceConfig.Link;
				expectedStatuses.add(new BackstopStatus(resourceConfig.Name, link, testStatus));
			}
		}

		Collections.sort(expectedStatuses);
		
		Backstop backstop = new Backstop(cfg);
		List<BackstopStatus> actualStatuses = backstop.checkAll();

		Assert.assertEquals(expectedStatuses.size(), actualStatuses.size());
		for (int i = 0; i < expectedStatuses.size(); ++i) {
			BackstopStatus bstatusExpected = expectedStatuses.get(i);
			BackstopStatus bstatusActual = actualStatuses.get(i);
			Assert.assertEquals(bstatusExpected.getResource(), bstatusActual.getResource());

			Status statusExpected = bstatusExpected.getStatus();
			Status statusActual = bstatusActual.getStatus();
			TestResource.assertStatusesEqual(statusExpected, statusActual);
		}
		
		backstop.close();
	}

}

