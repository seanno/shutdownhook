//
// TEMPESTRESOURCE.JAVA
//
// Params: StationId, AccessToken
//

package com.shutdownhook.backstop;

import java.time.Duration;
import java.time.Instant;
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

public class TempestResource implements Checker
{
	private static final int MAX_OBS_AGE_MINUTES = 30;
	private static final double MIN_OBS_BATTERY_VOLTS = 2.35;

	private static final String TEMPEST_BASE_URL =
		"https://swd.weatherflow.com/swd/rest/";

	public void check(Map<String,String> params,
					  BackstopHelpers helpers,
					  List<Status> statuses) throws Exception {

		// this use of members is legit only becuase we know
		// resources are created and destroyed one-off for each check
		this.stationId = params.get("StationId");
		this.accessToken = params.get("AccessToken");
		this.helpers = helpers;
		
		List<String> devices = getTempestDevices();
		if (devices.size() == 0) throw new Exception("No Tempest Devices found");

		for (String deviceId : devices) {
			try {
				checkOneTempest(deviceId, statuses);
			}
			catch (Exception ex) {
				statuses.add(new Status(deviceId, StatusLevel.ERROR, ex.getMessage()));
			}
		}
	}

	// +-----------------+
	// | checkOneTempest |
	// +-----------------+

	private void checkOneTempest(String deviceId, List<Status> statuses) throws Exception {

		JsonObject json = getTempestJson("observations/device/" + deviceId);

		String reqStatus = json.get("status").getAsJsonObject().get("status_code").getAsString();
		if (!reqStatus.equals("0")) {
			statuses.add(new Status(deviceId, StatusLevel.ERROR, json.toString()));
		}

		JsonArray obs = json.get("obs").getAsJsonArray().get(0).getAsJsonArray();

		StringBuilder sbNotes = new StringBuilder();

		// AGE
		Instant when = Instant.ofEpochSecond(obs.get(0).getAsLong());
		long ageMinutes = Duration.between(when, Instant.now()).toMinutes();
		String ageStr = String.format("Last reading: %d minutes ago", ageMinutes);

		if (ageMinutes > MAX_OBS_AGE_MINUTES) {
			statuses.add(new Status("Age " + deviceId, StatusLevel.ERROR, ageStr));
			return; // if age is too old then don't bother with the rest
		}
		else {
			sbNotes.append(ageStr);
		}

		// TEMP
		double tempCelsius = obs.get(7).getAsDouble();
		String tempStr = String.format("Current temp: %.0fF", Convert.celsiusToFarenheit(tempCelsius));
		if (tempCelsius <= 0) {
			statuses.add(new Status("Temperature " + deviceId, StatusLevel.ERROR, tempStr + " FREEZING"));
		}
		else {
			if (sbNotes.length() > 0) sbNotes.append("; ");
			sbNotes.append(tempStr);
		}

		// BATTERY
		double battVolts = obs.get(16).getAsDouble();
		String battStr = String.format("Battery: %.2fv", battVolts);
		
		if (battVolts < MIN_OBS_BATTERY_VOLTS) {
			statuses.add(new Status("Battery " + deviceId, StatusLevel.ERROR, battStr));
		}
		else {
			if (sbNotes.length() > 0) sbNotes.append("; ");
			sbNotes.append(battStr);
		}

		if (sbNotes.length() > 0) {
			statuses.add(new Status(deviceId, StatusLevel.OK, sbNotes.toString()));
		}
	}
	
	// +-------------------+
	// | getTempestDevices |
	// +-------------------+

	private List<String> getTempestDevices() throws Exception {

		JsonObject json = getTempestJson("stations/" + stationId);
		
		JsonArray jsonDevices = json
			.get("stations").getAsJsonArray()
			.get(0).getAsJsonObject()
			.get("devices").getAsJsonArray();

		List<String> devices = new ArrayList<String>();
		
		for (int i = 0; i < jsonDevices.size(); ++i) {

			JsonObject device = jsonDevices.get(i).getAsJsonObject();
			String deviceType = device.get("device_type").getAsString();

			if ("ST".equals(deviceType)) {
				devices.add(device.get("device_id").getAsString());
			}
		}

		return(devices);
	}

	// +----------------+
	// | getTempestJson |
	// +----------------+

	private JsonObject getTempestJson(String relativeUrl) throws Exception {

		String url = TEMPEST_BASE_URL + relativeUrl;
		WebRequests.Params reqParams = new WebRequests.Params();
		reqParams.addQueryParam("token", accessToken);

		WebRequests.Response response =
			helpers.getRequests().fetch(url, reqParams);

		if (!response.successful()) {
			throw new Exception(String.format("%s failed: (%d) %s", relativeUrl,
											  response.Status, response.StatusText));
		}

		return(JsonParser.parseString(response.Body).getAsJsonObject());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private String stationId;
	private String accessToken;
	private BackstopHelpers helpers;
}
