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

		public TideStore.ClosestTriple[] QueryThresholds = {
			new TideStore.ClosestTriple(0.25, 0, 10),
			new TideStore.ClosestTriple(0.25, 1, 10),
			new TideStore.ClosestTriple(0.50, 2, 20),
			new TideStore.ClosestTriple(1.00, 3, 30)
		};

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
	
	// +---------------+
	// | forecastTides |
	// +---------------+

	public List<TideStore.Tide> forecastTides() throws Exception {
		/////////////////////////////////////////////////////////////////
		// nyi --- implement this similar to below but get back a list
		// and implement sort on distance of other variables.
		// will need to implement a forecast looker-upper using Tempest
		// and interpolating ?
		// NOW + 1 HOUR + 3 HOURS + 6 HOURS + 12 HOURS
		//
		// Tasks: 1. forecast interpolater
		//        2. query tides & forecasts per schedule
		//        3. sort each queryClosest list by metric distances
		//        4. (add next two extremes to results?)
		/////////////////////////////////////////////////////////////////
		return(null);
	}

	// +-----------------+
	// | imageForInstant |
	// +-----------------+
	
	public File imageForInstant(Instant when) throws Exception {
		
		long hourOfDay = when.atZone(GMT_ZONE).get(ChronoField.HOUR_OF_DAY);
		long dayOfYear = when.atZone(GMT_ZONE).get(ChronoField.DAY_OF_YEAR);
		NOAA.Prediction prediction = noaa.getPredictions(when).estimateTide(when);

		TideStore.ClosestTriple match =
			new TideStore.ClosestTriple(prediction.Height, hourOfDay, dayOfYear);

		List<TideStore.Tide> tides = store.queryClosest(match, cfg.QueryThresholds, 1);
		if (tides == null || tides.size() == 0) throw new Exception("no match");
		
		return(tides.get(0).ImageFile);
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
		tide.HourOfDay = now.atZone(GMT_ZONE).get(ChronoField.HOUR_OF_DAY);
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

	private final static Logger log = Logger.getLogger(Tides.class.getName());
}
