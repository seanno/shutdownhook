/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Start simplifying all the crazy optionality in the JDK down to something I can
// reuse without importing and re-learning the entire universe every time.
// Grow as needed.

// NOT THREADSAFE!

package com.shutdownhook.toolbox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class Timey
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+
	
	public Timey() {
		this(null, null);
	}
	
	public Timey(Instant instant, String zone) {
		setWhen(instant);
		setZone(zone);
	}

	// +-----+
	// | Set |
	// +-----+

	public void setToNow() {
		this.setWhen(null);
	}
	
	public void setWhen(Instant instant) {
		this.when = (instant == null ? Instant.now() : instant);
	}

	public void setZone(String zone) {
		this.zoneId = (zone == null ? ZoneId.systemDefault() : ZoneId.of(zone));
	}

	// +---------+
	// | Convert |
	// +---------+

	public ZonedDateTime asZonedDatetime() {
		return(when.atZone(zoneId));
	}

	// +--------+
	// | Format |
	// +--------+

	public String asInformalDateTimeString() {
		return(DateTimeFormatter
			   .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
			   .withZone(zoneId)
			   .format(when));
	}

	// +---------+
	// | Members |
	// +---------+

	private Instant when;
	private ZoneId zoneId;
}
