//
// TEMPSTICKRESOURCE.JAVA
//
// Params: apiKey
//

package com.shutdownhook.backstop;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

import com.shutdownhook.backstop.Resource.Checker;
import com.shutdownhook.backstop.Resource.Config;
import com.shutdownhook.backstop.Resource.Status;
import com.shutdownhook.backstop.Resource.StatusLevel;

public class TempStickResource implements Checker
{
	private static final int MAX_AGE_MINUTES = 150; 
	private static final int MIN_BATT_PCT = 10;

	private static final String TEMPSTICK_URL =
		"https://tempstickapi.com/api/v1/sensors/all";

	private static final String TEMPSTICK_KEY_HEADER =
		"X-API-KEY";

	// +-------+
	// | check |
	// +-------+

	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  List<Status> statuses) throws Exception {


		String apiKey = params.get("apiKey");
		if (Easy.nullOrEmpty(apiKey)) throw new Error("apiKey parameter missing");

		try {
			Sensor[] sensors = getSensorData(apiKey, helpers);
			if (sensors == null || sensors.length == 0) throw new Exception("no sensors found");

			for (Sensor sensor : sensors) {
				try {
					checkSensor(sensor, statuses);
				}
				catch (Exception e2) {
					statuses.add(new Status(sensor.sensor_id, StatusLevel.ERROR, e2.getMessage()));
				}
			}
		}
		catch (Exception ex) {
			statuses.add(new Status("", StatusLevel.ERROR, ex.getMessage()));
		}
	}

	// +-------------+
	// | checkSensor |
	// +-------------+

	private void checkSensor(Sensor s, List<Status> statuses) throws Exception {

		StringBuilder sbNotes = new StringBuilder();
		
		// AGE
		Instant when = ZonedDateTime.parse(s.last_checkin, dtf).toInstant();
		long ageMinutes = Duration.between(when, Instant.now()).toMinutes();
		String ageStr = String.format("Last checkin %d minutes ago", ageMinutes);

		if (ageMinutes > MAX_AGE_MINUTES) {
			statuses.add(new Status("Age " + s.sensor_id, StatusLevel.ERROR, ageStr));
			return; // if age is too old then don't bother with the rest
		}
		else {
			sbNotes.append(ageStr);
		}

		// TEMP
		String tempStr = String.format("Current temp %.0fF", Convert.celsiusToFarenheit(s.last_temp));
		if (s.last_temp >= 0.0) {
			statuses.add(new Status("Temperature " + s.sensor_id, StatusLevel.ERROR, tempStr + " ABOVE FREEZING"));
		}
		else {
			if (sbNotes.length() > 0) sbNotes.append("; ");
			sbNotes.append(tempStr);
		}

		// BATTERY
		String battStr = String.format("Battery %d%%", s.battery_pct);
		if (s.battery_pct < MIN_BATT_PCT) {
			statuses.add(new Status("Battery " + s.sensor_id, StatusLevel.ERROR, battStr));
		}
		else {
			if (sbNotes.length() > 0) sbNotes.append("; ");
			sbNotes.append(battStr);
		}

		if (sbNotes.length() > 0) {
			statuses.add(new Status(s.sensor_id, StatusLevel.OK, sbNotes.toString()));
		}
	}

	// +---------------+
	// | getSensorData |
	// +---------------+

	public static class Sensor
	{
		public String sensor_id;
		public Double last_temp;
		public Integer battery_pct;
		public String last_checkin;
	}

	private Sensor[] getSensorData(String apiKey, BackstopHelpers helpers) throws Exception {

		JsonObject json = getSensorJson(apiKey, helpers);
		
		String sensorsArrayJson = json
			.get("data").getAsJsonObject()
			.get("items").getAsJsonArray()
			.toString();

		return(helpers.getGson().fromJson(sensorsArrayJson, Sensor[].class));
	}
	
	private JsonObject getSensorJson(String apiKey, BackstopHelpers helpers) throws Exception {
		
		WebRequests.Params reqParams = new WebRequests.Params();
		reqParams.addHeader(TEMPSTICK_KEY_HEADER, apiKey);
		reqParams.ForceGzip = true; 

		WebRequests.Response response =
			helpers.getRequests().fetch(TEMPSTICK_URL, reqParams);

		if (!response.successful()) {
			throw new Exception(String.format("%s failed: (%d) %s", TEMPSTICK_URL,
											  response.Status, response.StatusText));
		}

		return(JsonParser.parseString(response.Body).getAsJsonObject());
	}

	// +---------+
	// | Members |
	// +---------+
	
	private DateTimeFormatter dtf =
		DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
}
