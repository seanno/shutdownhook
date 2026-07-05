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
		public String TimeStampFormat = "'[It is now 'EE, LLLL dd, yyyy, HH:mm:ss z'] '";
		public String FileStampFormat = "YYYY-MM-dd-HHmmss";

		public String LocationFormat = " Your physical location is %s.";
		public String TimeZoneFormat = " Your local time zone is %s.";
	}
	
	public Environment(Config cfg) throws Exception {
		this.cfg = cfg;
		this.dtfTime = DateTimeFormatter.ofPattern(cfg.TimeStampFormat);
		this.dtfFile = DateTimeFormatter.ofPattern(cfg.FileStampFormat);
		this.zoneId = ZoneId.of(cfg.TimeZone);
	}

	// +--------------+
	// | getTimeStamp |
	// | getFileStamp |
	// +--------------+

	public String getTimeStamp() {
		return(ZonedDateTime.now(zoneId).format(dtfTime));
	}

	public String getFileStamp() {
		return(ZonedDateTime.now(zoneId).format(dtfFile));
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
		json.addProperty("time", getTimeStamp());

		return(json);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private DateTimeFormatter dtfTime;
	private DateTimeFormatter dtfFile;
	private ZoneId zoneId;

	private final static Logger log = Logger.getLogger(Environment.class.getName());
}
