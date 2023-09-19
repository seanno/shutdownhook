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

		public Double EqThreshold_UV = 1.0;
		public Double EqThreshold_PressureMb = 2.0;
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
		public Weather.Metrics WeatherMetrics; // predicted weather if available
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
		
		return(forecastTidesInternal(timepoints, maxExtremes, false));
	}
	
	public TideForecasts forecastTidesInternal(List<Instant> timepoints,
											   int maxExtremes,
											   boolean excludeTimepoint) throws Exception {

		TideForecasts forecasts = new TideForecasts();

		Weather weather = new Weather(tempest, timepoints);

		Instant first = timepoints.get(0);
		NOAA.Predictions tides = noaa.getPredictions(first);
		NOAA.Predictions extremes = tides.nextExtremes(first, maxExtremes);
		
		for (NOAA.Prediction prediction : extremes) {
			TideExtreme extreme = new TideExtreme();
			forecasts.Extremes.add(extreme);
			extreme.Forecast = forecastTide(prediction.Time, tides, weather, excludeTimepoint);
			extreme.Type = prediction.PredictionType;
		}

		for (Instant when : timepoints) {
			if (when.isAfter(tides.latestPrediction())) tides = noaa.getPredictions(when);
			forecasts.Forecasts.add(forecastTide(when, tides, weather, excludeTimepoint));
		}
		
		return(forecasts);
	}

	// +--------------+
	// | forecastTide |
	// +--------------+

	public TideForecast forecastTide(Instant when) throws Exception {

		List<Instant> timepoints = new ArrayList<Instant>();
		timepoints.add(when);

		TideForecasts forecasts = forecastTidesInternal(timepoints, 0, false);
		if (forecasts.Forecasts.size() == 0) return(null);

		return(forecasts.Forecasts.get(0));
	}
			
	private TideForecast forecastTide(Instant when,
									  NOAA.Predictions tides,
									  Weather weather,
									  boolean excludeTimepoint) throws Exception {

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
														   cfg.QueryForecastMaxResults,
														   (excludeTimepoint ? when : null));

		if (closestTides == null || closestTides.size() == 0) return(null);

		result.WeatherMetrics = weather.interpolateMetrics(when);

		if (result.WeatherMetrics != null) {

			Collections.sort(closestTides, new Comparator<TideStore.Tide>() {
					
				public int compare(TideStore.Tide t1, TideStore.Tide t2) {
					
					if (t1 == t2) return(0);
					if (t1 == null) return(1);
					if (t2 == null) return(-1);

					Weather.Metrics w1 = t1.WeatherMetrics;
					Weather.Metrics w2 = t2.WeatherMetrics;
					Weather.Metrics wT = result.WeatherMetrics;

					int diff = compareDoubleDiffs(w1.UV, w2.UV, wT.UV,
												  cfg.EqThreshold_UV);
					if (diff != 0) return(diff);

					diff = compareDoubleDiffs(w1.PressureMb, w2.PressureMb, wT.PressureMb,
											  cfg.EqThreshold_PressureMb);
					if (diff != 0) return(diff);

					diff = compareDoubleDiffs(w1.WindMps, w2.WindMps, wT.WindMps,
											  cfg.EqThreshold_WindMps);
					if (diff != 0) return(diff);

					diff = compareDoubleDiffs(w1.Humidity, w2.Humidity, wT.Humidity,
											  cfg.EqThreshold_Humidity);
					if (diff != 0) return(diff);

					return(0);
				}
			});
		}

		//outputAlternatives(tidePrediction.Height, minuteOfDay, dayOfYear, result.Weather, closestTides);
	
		result.Tide = closestTides.get(0);
		return(result);
	}

	private int compareDoubleAllOrNothing(Double d1, Double d2, Double target) {

		if (d1 == null || d2 == null || target == null) return(0);
		
		return(compareDoubleDiffs(d1 == 0.0 ? 0.0 : 1.0,
								  d2 == 0.0 ? 0.0 : 1.0,
								  target == 0.0 ? 0.0 : 1.0,
								  0.0));
	}
	
	private int compareDoubleDiffs(Double d1, Double d2, Double target, Double eqThreshold) {

		if (d1 == null || d2 == null || target == null) return(0);

		double diff1 = d1 - target; if (diff1 < 0) diff1 *= -1;
		double diff2 = d2 - target; if (diff2 < 0) diff2 *= -1;
		double dd = diff1 - diff2; if (dd < 0) dd *= -1;

		
		if (dd <= eqThreshold) return(0);
		if (diff1 < diff2) return(-1);
		if (diff1 > diff2) return(1);
		return(0);
	}

	private void outputAlternatives(double tgtHeight, int tgtMoD, int tgtDoY,
									Weather.Metrics tgtWeather, List<TideStore.Tide> alternatives) {

		System.out.println("=== TARGETS");
		System.out.println(String.format("HEIGHT: %02f", tgtHeight));
		System.out.println(String.format("MINUTEOFDAY: %d", tgtMoD));
		System.out.println(String.format("DAYOFYEAR: %d", tgtDoY));

		if (tgtWeather != null) {
			System.out.println(String.format("UV: %02f", tgtWeather.UV));
			System.out.println(String.format("WindMps: %02f", tgtWeather.WindMps));
			System.out.println(String.format("TempF: %02f", tgtWeather.TempF));
			System.out.println(String.format("PresureMb: %02f", tgtWeather.PressureMb));
			System.out.println(String.format("Humidity: %02f", tgtWeather.Humidity));
		}

		System.out.println("=== ALTERNATIVES");
		System.out.println(TideStore.Tide.CSV_HEADERS);
		for (TideStore.Tide t : alternatives) System.out.println(t.toCSV());
	}
																	
	// +-----------------+
	// | testPredictions |
	// +-----------------+

	public static class PredictionTest
	{
		public TideStore.Tide Reference;
		public TideForecast Forecast;
	}

	public List<PredictionTest> testPredictions(Instant onDay) throws Exception {

		List<TideStore.Tide> tides = store.getTidesForDay(onDay);
		
		List<Instant> timepoints = new ArrayList<Instant>();
		for (TideStore.Tide tide : tides) {
			timepoints.add(Instant.ofEpochSecond(tide.EpochSecond));
		}
		
		TideForecasts forecasts = forecastTidesInternal(timepoints, 0, true);

		List<PredictionTest> tests = new ArrayList<PredictionTest>();
		for (int i = 0; i < tides.size(); ++i) {
			PredictionTest test = new PredictionTest();
			tests.add(test);
			
			test.Reference = tides.get(i);
			test.Forecast = forecasts.Forecasts.get(i);
		}

		return(tests);
	}

	// +------------+
	// | getTide    |
	// | getTideUrl |
	// +------------+

	public TideStore.Tide getTide(String id) throws Exception {
		return(store.getTide(id));
	}

	public String getTideUrl(TideStore.Tide tide) throws Exception {
		return(store.urlForTide(tide));
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

		String stationId = cfg.Tempest.Stations.get(0).StationId;
		tide.WeatherMetrics = Weather.getCurrent(tempest, stationId);

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
