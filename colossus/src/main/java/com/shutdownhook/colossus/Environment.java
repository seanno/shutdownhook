//
// ENVIRONMENT.JAVA
//

package com.shutdownhook.colossus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

public class Environment
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+
	
	public static class Config
	{
		public String LocationString;
		public String TimeZone = "UTC";
		public String TimeFormat = "'[It is now 'EE, LLLL dd, yyyy, HH:mm:ss z'] '";

		public String LocationFormat = " Your physical location is %s.";
		public String TimeZoneFormat = " Your local time zone is %s.";
	}
	
	public Environment(Config cfg) throws Exception {
		this.cfg = cfg;
		this.dtf = DateTimeFormatter.ofPattern(cfg.TimeFormat);
		this.zoneId = ZoneId.of(cfg.TimeZone);
	}

	// +--------------+
	// | getTimestamp |
	// +--------------+

	public String getTimestamp() {
		return(ZonedDateTime.now(zoneId).format(dtf));
	}

	// +-------------+
	// | getLocation |
	// | getTimeZone |
	// +-------------+

	public String getLocation() {
		return(cfg.LocationString == null ? "" : String.format(cfg.LocationFormat, cfg.LocationString));
	}
	
	public String getTimeZone() {
		return(cfg.TimeZone == null ? "" : String.format(cfg.TimeZoneFormat, cfg.TimeZone));
	}

	// +---------+
	// | getJson |
	// +---------+

	public JsonObject getJson() {

		JsonObject json = new JsonObject();

		if (cfg.LocationString != null) json.addProperty("location", cfg.LocationString);
		if (cfg.TimeZone != null) json.addProperty("timezone", cfg.TimeZone);
		json.addProperty("time", getTimestamp());

		return(json);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private DateTimeFormatter dtf;
	private ZoneId zoneId;

	private final static Logger log = Logger.getLogger(Environment.class.getName());
}
