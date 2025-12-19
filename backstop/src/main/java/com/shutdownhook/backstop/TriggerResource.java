//
// TRIGGERRESOURCE.JAVA
//
// Params: link = optional link to open spreadsheet
//         tsv = url of sheet (File > Publish to Web > TSV)
//         skipHeaders = true if TSV includes column headers in row 1 (default DEFAULT_SKIP below)
//         dateFormat = date format per java.time.format.DateTimeFormatter (default DEFAULT_DTF below)
//         dateZoneId = zone used to offset "now" (default DEFAULT_ZONEID below)
//
// TSV columns: 1 = Action description
//              2 = Next occurrence due
//              3 = Optional days before due date to warn (default 0)
//              4 = Optional "snooze until" date to suppress messages
//            ... = additional columns allowed and ignored    
//

package com.shutdownhook.backstop;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class TriggerResource implements Checker
{
	private final static boolean DEFAULT_SKIP = false;
	private final static String DEFAULT_DTF = "M/d/[uuuu][uu]";
	private final static String DEFAULT_ZONEID = null;

	private final static String LINK_ROW_TOKEN = "{{ROW}}";
	
	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  List<Status> statuses) throws Exception {

		String tsv = params.get("tsv");
		String link = params.get("link");
		
		String skipHeadersStr = params.get("skipHeaders");
		boolean skipHeaders = (Easy.nullOrEmpty(skipHeadersStr)
							   ? DEFAULT_SKIP : Boolean.parseBoolean(skipHeadersStr));

		String dateFormatStr = params.get("dateFormat");
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern(Easy.nullOrEmpty(dateFormatStr)
															? DEFAULT_DTF : dateFormatStr);

		String zoneIdStr = params.get("dateZoneId");
		if (Easy.nullOrEmpty(zoneIdStr)) zoneIdStr = DEFAULT_ZONEID;
		ZoneId zoneId = (Easy.nullOrEmpty(zoneIdStr) ? ZoneId.systemDefault() : ZoneId.of(zoneIdStr));

		LocalDate today = LocalDate.now(zoneId);
			
		WebRequests.Response response = helpers.getRequests().fetch(tsv);
		if (!response.successful()) {
			throw new Exception(String.format("%s failed: (%d) %s", tsv,
											  response.Status, response.StatusText));
		}

		String[] lines = response.Body.split("\n");
		for (int i = (skipHeaders ? 1 : 0); i < lines.length; ++i) {
			String line = lines[i].trim();
			if (Easy.nullOrEmpty(line)) continue;

			String rowLink = (Easy.nullOrEmpty(link) ? null :
							  link.replace(LINK_ROW_TOKEN, Integer.toString(i+1)));

			try {
				handleOneRow(line.split("\t"), dtf, today, rowLink, statuses);
			}
			catch (Exception e) {
				statuses.add(new Status("ROW " + Integer.toString(i), StatusLevel.ERROR,
										String.format("Exception %s at row %d (%s)",
													  e.getMessage(), i+1, line, rowLink)));
			}
		}

		// note if we exit with statuses empty, OK row will be returned
	}

	private void handleOneRow(String[] fields, DateTimeFormatter dtf,
							  LocalDate today, String rowLink,
							  List<Status> statuses) throws Exception {
		
		if (fields.length < 2) throw new Exception("Malformed row");

		String snoozeStr = (fields.length >= 4 ? fields[3] : null);
		if (!Easy.nullOrEmpty(snoozeStr)) {
			LocalDate snoozeUntil = LocalDate.parse(snoozeStr, dtf);
			if (today.isBefore(snoozeUntil)) return;
		}

		String action = fields[0];
			
		String dueStr = fields[1];
		LocalDate due = LocalDate.parse(dueStr, dtf);

		if (due.isBefore(today)) {
			statuses.add(new Status(action, StatusLevel.ERROR, "Past Due Date: " + dueStr, rowLink));
			return;
		}

		if (due.equals(today)) {
			statuses.add(new Status(action, StatusLevel.WARNING, "DUE TODAY", rowLink));
			return;
		}

		String warnStr = (fields.length >= 3 ? fields[2] : null);
		int warnDays = (Easy.nullOrEmpty(warnStr) ? 0 : Integer.parseInt(warnStr));
		if (warnDays <= 0) return;

		LocalDate warn = due.minus(warnDays, ChronoUnit.DAYS);

		if (warn.isBefore(today)) {
			statuses.add(new Status(action, StatusLevel.WARNING, "Upcoming Due Date: " + dueStr, rowLink));
		}
	}

}
