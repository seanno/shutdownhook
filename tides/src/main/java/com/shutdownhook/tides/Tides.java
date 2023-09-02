/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.Closeable;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.weather.Tempest;

public class Tides implements Closeable
{
	private final static int TEMPEST_FORECAST_HOURS = 229;

	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public Tempest.Config Tempest;
		public TideStore.Config Store;
		public Camera.Config Camera;
		public NOAA.Config NOAA;

		// height, minutes, days
		public TideStore.ClosestTriple[] QueryThresholds = {
			new TideStore.ClosestTriple(0.25, 20, 10),
			new TideStore.ClosestTriple(0.50, 20, 20),
			new TideStore.ClosestTriple(1.00, 20, 30),
			new TideStore.ClosestTriple(1.00, 45, 30),
			new TideStore.ClosestTriple(1.00, 60, 30),
			new TideStore.ClosestTriple(1.00, 120, 120)
		};

		public Double EqThreshold_WindMps = 0.5;
		public Double EqThreshold_Humidity = 2.0;

		public Integer QueryForecastMaxResults = 10;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Tides(Config cfg) throws Exception {
		
		this.cfg = cfg;
		this.tempest = new Tempest(cfg.Tempest);
		this.store = new TideStore(cfg.Store);
		this.camera = new Camera(cfg.Camera);
		this.noaa = new NOAA(cfg.NOAA);
	}

	public void close() {

		if (tempest != null) tempest.close();
		if (camera != null) camera.close();
		if (noaa != null) noaa.close();

		tempest = null;
		store = null;
		camera = null;
		noaa = null;
	}
	
	// +----------------------------+
	// | forecastTides - Structures |
	// +----------------------------+

	public static class TideForecast
	{
		public Instant When;
		public Double Height;
		public NOAA.PredictionType PredictionType;
		public TideStore.Tide Tide; // matched tide record
		public Tempest.HourlyForecast Weather; // predicted weather if available
	}

	public static class TideExtreme 
	{
		public TideForecast Forecast;
		public NOAA.PredictionType Type;
	}
	
	public static class TideForecasts
	{
		public List<TideForecast> Forecasts = new ArrayList<TideForecast>();
		public List<TideExtreme> Extremes = new ArrayList<TideExtreme>();
	}
	
	// +---------------+
	// | forecastTides |
	// +---------------+

	// timepoints MUST be in ascending order
	
	public TideForecasts forecastTides(List<Instant> timepoints,
									   int maxExtremes) throws Exception {

		TideForecasts forecasts = new TideForecasts();
		
		Tempest.Forecast weather = null;
		if (weatherUseful(timepoints)) weather = tempest.getForecast();

		Instant first = timepoints.get(0);
		NOAA.Predictions tides = noaa.getPredictions(first);
		NOAA.Predictions extremes = tides.nextExtremes(first, maxExtremes);
		
		for (NOAA.Prediction prediction : extremes) {
			TideExtreme extreme = new TideExtreme();
			forecasts.Extremes.add(extreme);
			extreme.Forecast = forecastTide(prediction.Time, tides, weather);
			extreme.Type = prediction.PredictionType;
		}

		for (Instant when : timepoints) {
			if (when.isAfter(tides.latestPrediction())) tides = noaa.getPredictions(when);
			forecasts.Forecasts.add(forecastTide(when, tides, weather));
		}
		
		return(forecasts);
	}

	private boolean weatherUseful(List<Instant> timepoints) {

		Instant startRange = Instant.now();
		if (timepoints.get(timepoints.size() - 1).isBefore(startRange)) return(false);
		
		Instant endRange = startRange.plus(TEMPEST_FORECAST_HOURS, ChronoUnit.HOURS);
		if (timepoints.get(0).isAfter(endRange)) return(false);

		return(true);
	}

	// +--------------+
	// | forecastTide |
	// +--------------+

	public TideForecast forecastTide(Instant when) throws Exception {

		List<Instant> timepoints = new ArrayList<Instant>();
		timepoints.add(when);

		TideForecasts forecasts = forecastTides(timepoints, 0);
		if (forecasts.Forecasts.size() == 0) return(null);

		return(forecasts.Forecasts.get(0));
	}
			
	private TideForecast forecastTide(Instant when, NOAA.Predictions tides,
									  Tempest.Forecast weather) throws Exception {

		NOAA.Prediction tidePrediction = tides.estimateTide(when);

		TideForecast result = new TideForecast();
		result.When = when;
		result.Height = tidePrediction.Height;
		result.PredictionType = tidePrediction.PredictionType;
		
		int minuteOfDay = when.atZone(GMT_ZONE).get(ChronoField.MINUTE_OF_DAY);
		int dayOfYear = when.atZone(GMT_ZONE).get(ChronoField.DAY_OF_YEAR);
		
		TideStore.ClosestTriple match =
			new TideStore.ClosestTriple(tidePrediction.Height, minuteOfDay, dayOfYear);

		List<TideStore.Tide> closestTides = store.queryClosest(match,
															   cfg.QueryThresholds,
															   cfg.QueryForecastMaxResults);

		if (closestTides == null || closestTides.size() == 0) return(null);

		if (weather != null) {

			result.Weather = interpolateForecast(weather, when);

			Collections.sort(closestTides, new Comparator<TideStore.Tide>() {
					
				public int compare(TideStore.Tide t1, TideStore.Tide t2) {
					
					if (t1 == t2) return(0);
					if (t1 == null) return(1);
					if (t2 == null) return(-1);

					int diff = compareDoubleAllOrNothing(t1.PrecipMmph, t2.PrecipMmph,
														 result.Weather.precip);
					if (diff != 0) return(diff);

					diff = compareDoubleDiffs(t1.WindMps, t2.WindMps,
											  result.Weather.wind_avg, cfg.EqThreshold_WindMps);
					if (diff != 0) return(diff);

					diff = compareDoubleDiffs(t1.Humidity, t2.Humidity,
											  result.Weather.relative_humidity, cfg.EqThreshold_Humidity);
					if (diff != 0) return(diff);

					return(0);
				}
			});
		}

		//outputAlternatives(tidePrediction.Height, minuteOfDay, dayOfYear, result.Weather, closestTides);
	
		result.Tide = closestTides.get(0);
		return(result);
	}

	private int compareDoubleAllOrNothing(double d1, double d2, double target) {
		return(compareDoubleDiffs(d1 == 0.0 ? 0.0 : 1.0,
								  d2 == 0.0 ? 0.0 : 1.0,
								  target == 0.0 ? 0.0 : 1.0,
								  0.0));
	}
	
	private int compareDoubleDiffs(double d1, double d2, double target, double eqThreshold) {

		double diff1 = d1 - target; if (diff1 < 0) diff1 *= -1;
		double diff2 = d2 - target; if (diff2 < 0) diff2 *= -1;
		double dd = diff1 - diff2; if (dd < 0) dd *= -1;

		
		if (dd <= eqThreshold) return(0);
		if (diff1 < diff2) return(-1);
		if (diff1 > diff2) return(1);
		return(0);
	}

	private void outputAlternatives(double tgtHeight, int tgtMoD, int tgtDoY,
									Tempest.HourlyForecast tgtWeather,
									List<TideStore.Tide> alternatives) {

		System.out.println("=== TARGETS");
		System.out.println(String.format("HEIGHT: %02f", tgtHeight));
		System.out.println(String.format("MINUTEOFDAY: %d", tgtMoD));
		System.out.println(String.format("DAYOFYEAR: %d", tgtDoY));

		if (tgtWeather != null) {
			System.out.println(String.format("PrecipMmph: %02f", tgtWeather.precip));
			System.out.println(String.format("WindMps: %02f", tgtWeather.wind_avg));
			System.out.println(String.format("Humidity: %02f", tgtWeather.relative_humidity));
		}

		System.out.println("=== ALTERNATIVES");
		System.out.println(TideStore.Tide.CSV_HEADERS);
		for (TideStore.Tide t : alternatives) System.out.println(t.toCSV());
	}
																	
	// +---------------------+
	// | interpolateForecast |
	// +---------------------+

	// Helper to predict weather at a point of time --- iterpolating between
	// hourly forecasts from the tempest. By the time we get here, we've decided
	// to use the forecast data ... so if we're out of bounds do our best.
	
	private Tempest.HourlyForecast interpolateForecast(Tempest.Forecast forecast,
													   Instant when) {

		long whenEpochSec = when.getEpochSecond();
		
		List<Tempest.HourlyForecast> hourly = forecast.forecast.hourly;
		int len = hourly.size();

		// no forecast or too early 
		if (len == 0 || whenEpochSec < hourly.get(0).time) {
			return(currentToHourly(when, forecast.current_conditions));
		}

		// too late
		if (whenEpochSec > hourly.get(len - 1).time) {
			return(hourly.get(len - 1));
		}

		// find the first hourly forecast AFTER us.
		// we know we'll find one because of earlier checks
		int i = 1; // yes start with the second one!
		while (i < len && hourly.get(i).time < whenEpochSec) i++;

		// maybe it'll happen!
		if (whenEpochSec == hourly.get(i).time) return(hourly.get(i));

		Tempest.HourlyForecast low = hourly.get(i - 1);
		Tempest.HourlyForecast high = hourly.get(i);

		double fraction = (((double)(whenEpochSec - low.time)) /
						   ((double)(high.time - low.time)));

		Tempest.HourlyForecast est = new Tempest.HourlyForecast();

		est.time = whenEpochSec;
		est.local_hour = when.atZone(LOCAL_ZONE).get(ChronoField.HOUR_OF_DAY);
		est.local_day = when.atZone(LOCAL_ZONE).get(ChronoField.DAY_OF_MONTH);

		// for these we just do a linear interpolation
		est.air_temperature = interp(low.air_temperature, high.air_temperature, fraction);
		est.relative_humidity = interp(low.relative_humidity, high.relative_humidity, fraction);
		est.precip = interp(low.precip, high.precip, fraction);
		est.precip_probability = interp(low.precip_probability, high.precip_probability, fraction);
		est.wind_avg = interp(low.wind_avg, high.wind_avg, fraction);
		est.wind_gust = interp(low.wind_gust, high.wind_gust, fraction);
		est.feels_like = interp(low.feels_like, high.feels_like, fraction);
		
		// for these we just pick whichever is closer
		est.conditions = ((fraction < .5) ? low.conditions : high.conditions);
		est.icon = ((fraction < .5) ? low.icon : high.icon);
		
		est.wind_direction = ((fraction < .5) ? low.wind_direction
							  : high.wind_direction);
		
		est.wind_direction_cardinal = ((fraction < .5) ? low.wind_direction_cardinal
									   : high.wind_direction_cardinal);

		return(est);
	}

	private static Tempest.HourlyForecast currentToHourly(Instant when,
														  Tempest.CurrentConditions cc) {

		Tempest.HourlyForecast hf = new Tempest.HourlyForecast();
		
		hf.time = when.getEpochSecond();
		hf.local_hour = when.atZone(LOCAL_ZONE).get(ChronoField.HOUR_OF_DAY);
		hf.local_day = when.atZone(LOCAL_ZONE).get(ChronoField.DAY_OF_MONTH);
		
		hf.conditions = cc.conditions;
		hf.icon = cc.icon;
		hf.air_temperature = cc.air_temperature;
		hf.relative_humidity = cc.relative_humidity;
		hf.precip = 0.0; // dunno
		hf.precip_probability = 0.0; // dunno
		hf.wind_avg = cc.wind_avg;
		hf.wind_direction = cc.wind_direction;
		hf.wind_direction_cardinal = cc.wind_direction_cardinal;
		hf.wind_gust = cc.wind_gust;
		hf.feels_like = cc.feels_like;

		return(hf);
	}


	private static double interp(double low, double high, double fraction) {
		return(low + ((high - low) * fraction));
	}

	// +---------+
	// | getTide |
	// +---------+

	public TideStore.Tide getTide(String id) throws Exception {
		return(store.getTide(id));
	}

	// +--------------------+
	// | captureCurrentTide |
	// +--------------------+

	// Goes to external sources to get the current tide, including the Lux
	// and image, saves it all to the DB, and returns a complete Tide structure
	// (which the caller probalby doesn't need but hey)
	
	public TideStore.Tide captureCurrentTide() throws Exception {

		// time
		
		Instant now = Instant.now();

		TideStore.Tide tide = new TideStore.Tide();
		tide.EpochSecond = now.getEpochSecond();
		tide.MinuteOfDay = now.atZone(GMT_ZONE).get(ChronoField.MINUTE_OF_DAY);
		tide.DayOfYear = now.atZone(GMT_ZONE).get(ChronoField.DAY_OF_YEAR);

		// weather

		Tempest.Observation obs =
			tempest.getObservation(cfg.Tempest.Stations.get(0).StationId);

		Tempest.Metrics metrics = obs.obs.get(0);
		
		tide.UV = metrics.uv;
		tide.Lux = metrics.brightness;
		tide.WindMps = metrics.wind_avg;
		tide.PrecipMmph = metrics.precip;
		tide.PressureMb = metrics.sea_level_pressure;
		tide.Humidity = metrics.relative_humidity;

		double tempC = metrics.air_temperature;
		tide.TempF = Convert.celsiusToFarenheit(tempC);

		// tide

		NOAA.Predictions predictions = noaa.getPredictions();
		tide.TideHeight = predictions.estimateTide().Height;

		// image
		
		File snapshot = camera.takeSnapshot();

		// ... and store it!
		tide = store.saveTide(tide, snapshot);

		return(tide);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Tempest tempest;
	private TideStore store;
	private Camera camera;
	private NOAA noaa;

	private final static ZoneId GMT_ZONE = ZoneId.of("GMT");
	private final static ZoneId LOCAL_ZONE = ZoneId.systemDefault();

	private final static Logger log = Logger.getLogger(Tides.class.getName());
}
