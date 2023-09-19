/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.weather.Tempest;

public class Weather 
{
	// in all cases the lists must already be sorted by time ascending!
	
	public Weather(Tempest tempest, List<Instant> timepoints) throws Exception {
		this.metrics = (useful(timepoints) ? metricsFromForecast(tempest) : null);
	}

	public Weather(List<TideStore.Tide> tides) {
		this.metrics = new ArrayList<Metrics>();
		for (TideStore.Tide tide : tides) metrics.add(tide.WeatherMetrics);
	}

	// +---------------+
	// | weatherUseful |
	// +---------------+

	// return true if any of start/end would be in range of a forecast

	private final static int TEMPEST_FORECAST_HOURS = 229;

	static public boolean useful(List<Instant> timepointsAscending) {

		int c = timepointsAscending.size();
		if (c == 0) return(false);
		
		return(useful(timepointsAscending.get(0),
					  timepointsAscending.get(c - 1)));
	}
	
	static public boolean useful(Instant start, Instant end) {

		Instant now = Instant.now();
		if (end.isBefore(now)) return(false);

		Instant furthest = now.plus(TEMPEST_FORECAST_HOURS, ChronoUnit.HOURS);
		if (start.isAfter(furthest)) return(false);

		return(true);
	}

	// +---------+
	// | Metrics |
	// +---------+

	public static class Metrics
	{
		public Long EpochSecond;
		public Double UV;
		public Double WindMps;
		public Double TempF;
		public Double PressureMb;
		public Double Humidity;
		public Double Lux;
		
		static public Metrics fromTempestCC(Tempest.CurrentConditions cc) {

			Metrics m = new Metrics();
			
			m.EpochSecond = cc.time;
			m.UV = cc.uv;
			m.WindMps = cc.wind_avg;
			m.TempF = Convert.celsiusToFarenheit(cc.air_temperature);
			m.PressureMb = cc.sea_level_pressure;
			m.Humidity = cc.relative_humidity;
			m.Lux = null;

			return(m);
		}

		static public Metrics fromTempestHourly(Tempest.HourlyForecast hf) {

			Metrics m = new Metrics();
			
			m.EpochSecond = hf.time;
			m.UV = hf.uv;
			m.WindMps = hf.wind_avg;
			m.TempF = Convert.celsiusToFarenheit(hf.air_temperature);
			m.PressureMb = hf.sea_level_pressure;
			m.Humidity = hf.relative_humidity;
			m.Lux = null;

			return(m);
		}

		static public Metrics fromTempestObservation(Tempest.Observation obs) {

			Tempest.Metrics tm = obs.obs.get(0);
			
			Metrics m = new Metrics();
			
			m.EpochSecond = tm.timestamp;
			m.UV = tm.uv;
			m.WindMps = tm.wind_avg;
			m.TempF = Convert.celsiusToFarenheit(tm.air_temperature);
			m.PressureMb = tm.sea_level_pressure;
			m.Humidity = tm.relative_humidity;
			m.Lux = tm.brightness;

			return(m);
		}

		@Override
		public String toString() {
			return(String.format("EpochSecond=%d; UV=%.1f; WindMps=%.1f; " +
								 "TempF=%.1f; PressureMb=%.1f; Humidity=%.1f; Lux=%.1f",
								 EpochSecond, UV, WindMps, TempF, PressureMb,
								 Humidity, Lux));
		}
	}

	// +------------+
	// | getCurrent |
	// +------------+

	public static Metrics getCurrent(Tempest tempest,
									 String stationId) throws Exception {
		
		Tempest.Observation obs = tempest.getObservation(stationId);
		return(Metrics.fromTempestObservation(obs));
	}

	// +--------------------+
	// | interpolateMetrics |
	// +--------------------+

	public Metrics interpolateMetrics(Instant when) {
		
		long whenEpochSecond = when.getEpochSecond();

		// degenerate cases or before / after range
		
		if (metrics == null) return(null);
		
		int c = metrics.size();
		if (c == 0 || whenEpochSecond < metrics.get(0).EpochSecond) {
			return(metrics.get(0));
		}

		if (whenEpochSecond >= metrics.get(c - 1).EpochSecond) {
			return(metrics.get(c - 1));
		}
		
		// find the first hourly forecast AFTER us.
		// we know we'll find one because of earlier checks
		
		int i = 1; // yes start with the second one!
		while (i < c && metrics.get(i).EpochSecond < whenEpochSecond) i++;

		// maybe it'll happen!
		if (whenEpochSecond == metrics.get(i).EpochSecond) return(metrics.get(i));

		// interpolate
		Metrics low = metrics.get(i - 1);
		Metrics high = metrics.get(i);

		double fraction = (((double)(whenEpochSecond - low.EpochSecond)) /
						   ((double)(high.EpochSecond - low.EpochSecond)));

		Metrics m = new Metrics();
		
		m.EpochSecond = whenEpochSecond;
		m.UV = interp(low.UV, high.UV, fraction);
		m.WindMps = interp(low.WindMps, high.WindMps, fraction);
		m.TempF = interp(low.TempF, high.TempF, fraction);
		m.PressureMb = interp(low.PressureMb, high.PressureMb, fraction);
		m.Humidity = interp(low.Humidity, high.Humidity, fraction);

		return(m);
	}

	private static double interp(double low, double high, double fraction) {
		return(low + ((high - low) * fraction));
	}

	// +---------------------+
	// | metricsFromForecast |
	// +---------------------+

	private List<Metrics> metricsFromForecast(Tempest tempest) throws Exception {

		Tempest.Forecast forecast = tempest.getForecast();
		
		List<Metrics> metrics = new ArrayList<Metrics>();

		metrics.add(Metrics.fromTempestCC(forecast.current_conditions));

		for (Tempest.HourlyForecast hourly : forecast.forecast.hourly) {
			metrics.add(Metrics.fromTempestHourly(hourly));
		}

		return(metrics);
	}

	// +---------+
	// | Members |
	// +---------+

	private List<Metrics> metrics;

	private final static Logger log = Logger.getLogger(Weather.class.getName());
}
