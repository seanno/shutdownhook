//
// ENVIRONMENTTEST.JAVA
//

package com.shutdownhook.colossus;

import com.google.gson.JsonObject;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnvironmentTest
{
	// +---------+
	// | Helpers |
	// +---------+

	private Environment make(String location, String timezone) throws Exception {
		Environment.Config cfg = new Environment.Config();
		cfg.LocationString = location;
		if (timezone != null) cfg.TimeZone = timezone;
		return new Environment(cfg);
	}

	// +-------------+
	// | getLocation |
	// +-------------+

	@Test
	public void testGetLocationNullReturnsEmpty() throws Exception {
		Environment env = make(null, null);
		assertEquals("", env.getLocation());
	}

	@Test
	public void testGetLocationContainsValue() throws Exception {
		Environment env = make("Seattle", null);
		String loc = env.getLocation();
		assertTrue(loc.contains("Seattle"));
	}

	@Test
	public void testGetLocationUsesFormat() throws Exception {
		Environment.Config cfg = new Environment.Config();
		cfg.LocationString = "Tokyo";
		cfg.LocationFormat = "Location: %s.";
		Environment env = new Environment(cfg);
		assertEquals("Location: Tokyo.", env.getLocation());
	}

	// +-------------+
	// | getTimeZone |
	// +-------------+

	@Test
	public void testGetTimeZoneContainsValue() throws Exception {
		Environment env = make(null, "America/Los_Angeles");
		String tz = env.getTimeZone();
		assertTrue(tz.contains("America/Los_Angeles"));
	}

	@Test
	public void testGetTimeZoneUsesFormat() throws Exception {
		Environment.Config cfg = new Environment.Config();
		cfg.TimeZone = "UTC";
		cfg.TimeZoneFormat = "TZ=%s";
		Environment env = new Environment(cfg);
		assertEquals("TZ=UTC", env.getTimeZone());
	}

	// +--------------+
	// | getTimeStamp |
	// | getFileStamp |
	// +--------------+

	@Test
	public void testGetTimeStampNotEmpty() throws Exception {
		Environment env = make(null, null);
		String ts = env.getTimeStamp();
		assertNotNull(ts);
		assertFalse(ts.isEmpty());
	}

	@Test
	public void testGetFileStampMatchesExpectedPattern() throws Exception {
		Environment env = make(null, null);
		String fs = env.getFileStamp();
		// default format: YYYY-MM-dd-HHmmss e.g. "2026-07-08-143022"
		assertTrue("FileStamp '" + fs + "' did not match expected pattern",
			fs.matches("\\d{4}-\\d{2}-\\d{2}-\\d{6}"));
	}

	// +---------+
	// | getJson |
	// +---------+

	@Test
	public void testGetJsonContainsAllFieldsWhenSet() throws Exception {
		Environment env = make("Seattle", "UTC");
		JsonObject json = env.getJson();

		assertTrue(json.has("location"));
		assertTrue(json.has("timezone"));
		assertTrue(json.has("time"));
		assertEquals("Seattle", json.get("location").getAsString());
		assertEquals("UTC", json.get("timezone").getAsString());
	}

	@Test
	public void testGetJsonOmitsLocationWhenNull() throws Exception {
		Environment env = make(null, "UTC");
		JsonObject json = env.getJson();

		assertFalse(json.has("location"));
		assertTrue(json.has("timezone"));
		assertTrue(json.has("time"));
	}
}
