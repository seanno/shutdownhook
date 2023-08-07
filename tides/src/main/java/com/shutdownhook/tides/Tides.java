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
		
		Tempest.Forecast forecast =
			tempest.getForecast(cfg.Tempest.Stations.get(0).StationId);

		tide.Lux = forecast.current_conditions.brightness;
		tide.WindMps = forecast.current_conditions.wind_avg;

		double tempC = forecast.current_conditions.air_temperature;
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
