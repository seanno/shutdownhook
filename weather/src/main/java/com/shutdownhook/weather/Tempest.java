/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.io.Closeable;
import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class Tempest implements Closeable 
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public List<StationConfig> Stations = new ArrayList<StationConfig>();
		public String BaseUrl = "https://swd.weatherflow.com/swd/rest";
		public WebRequests.Config Requests = new WebRequests.Config();
	}

	public static class StationConfig
	{
		public String StationId;
		public String AccessToken;
	}
	
	public Tempest(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);
	}

	public void close() {
		requests.close();
	}
	
	// +------------+
	// | getStation |
	// +------------+

	public static class Station
	{
		public Status status;
		public List<Location> stations;
	}

	public static class Location
	{
		public String location_id;
		public String station_id;
		public String name;
		public String public_name;
		public Double latitude;
		public Double longitude;
		public List<Device> devices;
	}

	public static class Device
	{
		public String device_id;
		public String serial_number;
		public DeviceMeta device_meta;
		public String device_type;
		public String hardware_revision;
		public String firmware_revision;
		public String notes;
	}

	public static class DeviceMeta
	{
		public Double agl;
		public String name;
		public String environment;
		public String wifi_network_name;
	}

	public Station getStation(String stationId) throws Exception {
		String json = getTempestJson(stationId, "stations");
		return(new Gson().fromJson(json, Station.class));
	}

	// +----------------+
	// | getObservation |
	// +----------------+

	public static class Observation
	{
		public Status status;
		public String station_id;
		public List<Metrics> obs;
	}

	public static class Metrics
	{
		public Long timestamp; // epoch seconds
		public Double air_temperature; // degrees celsius
		public Double barometric_pressure; // millibars
		public Double sea_level_pressure; // millibars
		public Double relative_humidity; // percentage
		public Double precip; // millimeters over last 1min
		public Double precip_accum_last_1hr; // millimeters
		public Double wind_avg; // meters/sec avg over last 1min
		public Double wind_direction; // degrees from north
		public Double wind_gust; // meters/sec max over last 1min
		public Double wind_lull; // meters/sec min over last 1min
		public Double solar_radiation; // watts/square meter
		public Double uv; // uv index (1-10+)
		public Double brightness; // lux (lumens/square meter)
		public Long lightning_strike_last_epoch; // epoch seconds
		public Double lightning_strike_last_distance; // kilometers
		public Integer lightning_strike_count_last_3hr; // count
	}

	public Observation getObservation(String stationId) throws Exception {
		String json = getTempestJson(stationId, "observations/station");
		return(new Gson().fromJson(json, Observation.class));
	}

	// +-------------+
	// | getForecast |
	// +-------------+

	public static class Forecast
	{
		public Status status;
		public CurrentConditions current_conditions;
		public ForecastContainer forecast;
		public Units units; 
		public Double latitude;
		public Double longitude;
		public String timezone;
		public Integer timezone_offset_minutes;

		public FormattedSnapshot getCurrentSnap() {
			return(new FormattedSnapshot(current_conditions, null, null));
		}
		
		public FormattedSnapshot getHourlySnap(int i) {
			if (i >= forecast.hourly.size()) return(null);
			return(new FormattedSnapshot(null, forecast.hourly.get(i), null));
		}
			
		public FormattedSnapshot getDailySnap(int i) {
			if (i >= forecast.daily.size()) return(null);
			return(new FormattedSnapshot(null, null, forecast.daily.get(i)));
		}
	}

	public static class CurrentConditions
	{
		public Long time; // epoch seconds
		public String conditions;
		public String icon;
		public Double air_temperature; // degrees celsius 
		public Double sea_level_pressure; // millibars
		public Double station_pressure; // millibars
		public String pressure_trend;
		public Double relative_humidity; // percentage
		public Double wind_avg; // meters/sec avg over last 1min
		public Double wind_direction; // degrees from north
		public String wind_direction_cardinal;
		public String wind_direction_icon;
		public Double wind_gust; // meters/sec max over last 1min
		public Double solar_radiation; // watts/square meter
		public Double uv; // uv index (1-10+)
		public Double brightness; // lux (lumens/square meter)
		public Double feels_like; // degrees celsius 
		public Double dew_point; // degrees celsius 
		public Double wet_bulb_temperature; // degrees celsius 
		public Double delta_t; // air_temperature - wet_bulb_temperature ?
		public Double air_density; // kg/meter^3
		public Integer lightning_strike_count_last_1hr; // count
		public Integer lightning_strike_count_last_3hr; // count
		public Double lightning_strike_last_distance; // kilometers
		public String lightning_strike_last_distance_msg; 
		public Long lightning_strike_last_epoch; // epoch seconds
		public Double precip_accum_local_day; // millimeters
		public Double precip_accum_local_yesterday; // millimeters
		public Double precip_minutes_local_day; // minutes
		public Double precip_minutes_local_yesterday; // minutes
		public Boolean is_precip_local_day_rain_check; 
		public Boolean is_precip_local_yesterday_rain_check; 
	}

	public static class ForecastContainer
	{
		public List<DailyForecast> daily;
		public List<HourlyForecast> hourly;
	}

	public static class DailyForecast
	{
		public Long day_start_local; // epoch seconds
		public Integer day_num; // 1-31
		public Integer month_num; // 1-12
		public String conditions;
		public String icon;
		public Long sunrise; // epoch seconds
		public Long sunset; // epoch seconds
		public Double air_temp_high; // degrees celsius 
		public Double air_temp_low; // degrees celsius 
		public Double precip_probability; // percentage
		public String precip_icon;
		public String precip_type;
	}

	public static class HourlyForecast
	{
		public Long time; // epoch seconds
		public String conditions;
		public String icon;
		public Double air_temperature; // degrees celsius 
		public Double sea_level_pressure; // millibars
		public Double relative_humidity; // percentage
		public Double precip; // millimeters
		public Double precip_probability; // percentage
		public Double wind_avg; // meters/sec avg over last 1min
		public Double wind_direction; // degrees from north
		public String wind_direction_cardinal;
		public Double wind_gust; // meters/sec max over last 1min
		public Double uv; // uv index (1-10+)
		public Double feels_like; // degrees celsius 
		public Integer local_hour; // 0-23
		public Integer local_day; // 1-31
	}

	public static class Units
	{
		public String units_temp;
		public String units_wind;
		public String units_precip;
		public String units_pressure;
		public String units_distance;
		public String units_brightness;
		public String units_solar_radiation;
		public String units_other;
		public String units_air_density;
	}

	public static class FormattedSnapshot
	{
		public int LowTempF;
		public int HighTempF;
		public int WindMph;
		public String WindDir;
		public String Icon;
		public String Conditions;

		public CurrentConditions Current;
		public HourlyForecast Hourly;
		public DailyForecast Daily;

		public FormattedSnapshot(CurrentConditions current,
								 HourlyForecast hourly,
								 DailyForecast daily) {

			this.Current = current;
			this.Hourly = hourly;
			this.Daily = daily;

			if (current != null) {
				calcSnapshot(current.air_temperature,
							 current.air_temperature,
							 current.wind_avg,
							 current.wind_direction_cardinal,
							 current.icon,
							 current.conditions);
			}
			else if (hourly != null) {
				calcSnapshot(hourly.air_temperature,
							 hourly.air_temperature,
							 hourly.wind_avg,
							 hourly.wind_direction_cardinal,
							 hourly.icon,
							 hourly.conditions);
			}
			else {
				calcSnapshot(daily.air_temp_low,
							 daily.air_temp_high,
							 0.0,
							 "",
							 daily.icon,
							 daily.conditions);
			}
		}

		private void calcSnapshot(double lowCelsius, double highCelsius,
								  double mps, String dir, String icon,
								  String conditions) {

			LowTempF = (int) Math.round(Convert.celsiusToFarenheit(lowCelsius));
			HighTempF = (int) Math.round(Convert.celsiusToFarenheit(highCelsius));
			WindMph = (int) Math.round(Convert.metersToMiles(mps) * 60 * 60);
			WindDir = (WindMph > 0 ? dir : "");
			Icon = icon;
			Conditions = conditions;
		}
	}
	
	public Forecast getForecast(String stationId) throws Exception {
		WebRequests.Params params = new WebRequests.Params();
		params.addQueryParam("station_id", stationId);
		
		String json = getTempestJson(stationId, "/better_forecast", params);
		return(new Gson().fromJson(json, Forecast.class));
	}

	// +--------------+
	// | Shared Types |
	// +--------------+

	public static class Status
	{
		public String status_code; // 0 == success
		public String status_message;
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private String getTempestJson(String stationId, String resource) throws Exception {
		return(getTempestJson(stationId, resource + "/" + stationId, new WebRequests.Params()));
	}
	
	private String getTempestJson(String stationId, String relativeUrl,
								  WebRequests.Params params) throws Exception {

		// find access token
		
		String accessToken = null;
		
		for (StationConfig station : cfg.Stations) {
			if (station.StationId.equals(stationId)) {
				accessToken = station.AccessToken;
				break;
			}
		}

		if (accessToken == null) {
			throw new IllegalArgumentException("No configured station " + stationId);
		}

		// make the web call
		String url = Easy.urlPaste(cfg.BaseUrl, relativeUrl);
		params.addQueryParam("token", accessToken);
		
		WebRequests.Response response = requests.fetch(url, params);

		if (!response.successful()) {
			
			String msg = String.format("Error calling tempest (%d: %s)",
									   response.Status, response.StatusText);
			
			throw new Exception(msg, response.Ex);
		}

		return(response.Body);
	}

	private Config cfg;
	private WebRequests requests;

	// +------+
	// | main |
	// +------+

	public static void main(String[] args) throws Exception {

		if (args.length < 2) {

			System.out.println("Usage: java -cp [path_to_jar] \\\n" +
							   "\tcom.shutdownhook.weather.Tempest \\\n" +
							   "\t[station_id] \\\n" +
							   "\t[access_token] ");
			return;
		}
		
		StationConfig stationCfg = new StationConfig();
		stationCfg.StationId = args[0];
		stationCfg.AccessToken = args[1];

		Config cfg = new Config();
		cfg.Stations.add(stationCfg);

		Tempest tempest = null;

		try {
			tempest = new Tempest(cfg);

			Station station = tempest.getStation(stationCfg.StationId);
			Observation obs = tempest.getObservation(stationCfg.StationId);
			Forecast forecast = tempest.getForecast(stationCfg.StationId);
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();

			System.out.println("\n" + gson.toJson(station) + "\n");
			System.out.println("\n" + gson.toJson(obs) + "\n");
			System.out.println("\n" + gson.toJson(forecast) + "\n");
		}
		finally {
			
			if (tempest != null) tempest.close();
		}
		
	}
}
