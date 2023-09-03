/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.Closeable;
import java.lang.StringBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class NOAA implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String StationId;
		public String PredictionUrlFormat = DEFAULT_PREDICTION_URL_FMT;

		public WebRequests.Config Requests = new WebRequests.Config();
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public NOAA(Config cfg) throws Exception {

		this.cfg = cfg;
		this.requests = new WebRequests(cfg.Requests);

		dtfUrl = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(GMT_ZONE);
		dtfParse = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(GMT_ZONE);
	}

	public void close() {
		requests.close();
		requests = null;
	}

	// +-------------+
	// | Predictions |
	// +-------------+

	public static enum PredictionType
	{
		LOW,
		HIGH,
		POINT_RISING,
		POINT_FALLING,
		EST_RISING,
		EST_FALLING;

		public String toHTML() {
			
			String html = "";
			
			switch (this) {
				case LOW:
					html = "Low";
					break;
					
				case HIGH:
					html = "High";
					break;
					
				case POINT_RISING:
				case EST_RISING:
					html = "&uuarr;";
					break;
					
				case POINT_FALLING: 
				case EST_FALLING:
					html = "&ddarr;";
					break;
			}
			
			return(html);
		}
	}
	
	public static class Prediction implements Comparable<Prediction>
	{
		public Instant Time;
		public Double Height;
		public PredictionType PredictionType;

		public int compareTo(Prediction p) {
			return(Time.compareTo(p.Time));
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) return(true);
			if (!(o instanceof Prediction)) return(false); // catches null
			return(Time.equals(((Prediction)o).Time));
		}

		@Override
		public String toString() {
			return(String.format("%s\t%s\t%f", Time, PredictionType, Height));
		}
	}

	public static class Predictions extends ArrayList<Prediction>
	{
		// +--------------+
		// | nextExtremes |
		// +--------------+
		
		public Predictions nextExtremes(int max) throws IllegalArgumentException {
			return(nextExtremes(Instant.now(), max));
		}

		public Predictions nextExtremes(Instant when, int max) throws IllegalArgumentException {

			int pos = binarySearch(when);
			if (pos < 0) pos = -(pos + 1);

			Predictions extremes = new Predictions();
			for (int i = pos; i < size(); ++i) {
				Prediction p = get(i);
				PredictionType ptype = p.PredictionType;
				if (ptype == PredictionType.LOW || ptype == PredictionType.HIGH) {
					extremes.add(p);
					if (extremes.size() >= max) break;
				}
			}

			return(extremes);
		}
		
		// +--------------+
		// | estimateTide |
		// +--------------+

		public Prediction estimateTide() throws IllegalArgumentException {
			return(estimateTide(Instant.now()));
		}

		public Prediction estimateTide(Instant when) throws IllegalArgumentException {

			int pos = binarySearch(when);
			if (pos >= 0) return(get(pos));

			// calculations below will always be valid if array is sorted
			// (which we control) and the tests in binarySearch lets us get here

			int insertionPoint = -(pos + 1);
			Prediction pLow = get(insertionPoint - 1);
			Prediction pHigh = get(insertionPoint);

			// fraction of time through period
			double timeWhole = (double) pLow.Time.until(pHigh.Time, ChronoUnit.MILLIS);
			double timeFraction = ((double) pLow.Time.until(when, ChronoUnit.MILLIS)) / timeWhole;

			// tide change through period (may be - if going down, that's ok)
			double tideWhole = (pHigh.Height - pLow.Height);
			
			PredictionType pt = (tideWhole > 0 ? PredictionType.EST_RISING
								 : PredictionType.EST_FALLING);
				
			// and interpolate!
			double estimate = pLow.Height + (tideWhole * timeFraction);

			Prediction pEst = new Prediction();
			pEst.Time = when;
			pEst.Height = estimate;
			pEst.PredictionType = pt;
			return(pEst);
		}

		public Instant earliestPrediction() { return(this.get(0).Time); }
		public Instant latestPrediction() { return(this.get(this.size() - 1).Time); }
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < size(); ++i) {
				sb.append(String.format("%d\t%s\n", i, get(i)));
			}
			return(sb.toString());
		}

		private int binarySearch(Instant when) throws IllegalArgumentException {
			
			if (size() == 0 ||
				when.isBefore(get(0).Time) ||
				when.isAfter(get(size() - 1).Time)) {

				throw new IllegalArgumentException("outside predictions range");
			}

			// same semantics as Collections.binarySearch
			Prediction pKey = new Prediction(); pKey.Time = when;
			return(Collections.binarySearch(this, pKey));
		}

		// must be called for alternating H/L points in ascending time order.
		// uses the "rule of 12" to add intermediate tide points between extremes
	    private void addTidePoint(Instant when, double height, boolean low) {
			addTwelfths(when, height);
			addOne(when, height, (low ? PredictionType.LOW : PredictionType.HIGH));
		}

		private void addTwelfths(Instant when, double height) {
			
			if (size() == 0) return;
			
			Prediction pLast = get(size() - 1);

			// note this can be positive or negative which is what we want
			double heightTwelfth = (height - pLast.Height) / 12.0;
			PredictionType pt = (heightTwelfth > 0 ? PredictionType.POINT_RISING
								 : PredictionType.POINT_FALLING);

			long secsBetween =
				Duration.between(pLast.Time, when).getSeconds()
				/ (TIDE_MULTS.length + 1);
												   
			Instant interWhen = pLast.Time;
			double interHeight = pLast.Height;

			for (double mult : TIDE_MULTS) {
				interWhen = interWhen.plusSeconds(secsBetween);
				interHeight += (heightTwelfth * mult);
				addOne(interWhen, interHeight, pt);
			}
		}

		private void addOne(Instant when, double height,
							PredictionType predictionType) {

			Prediction p = new Prediction();
			p.Time = when;
			p.Height = height;
			p.PredictionType = predictionType;
			
			add(p);
		}
	}

	// +----------------+
	// | getPredictions |
	// +----------------+

	public Predictions getPredictions() throws Exception {
		return(getPredictions(Instant.now()));
	}
	
	public Predictions getPredictions(Instant when) throws Exception {

		NOAAPredictions noaaPredictions = getNOAAPredictions(when);

		Predictions predictions = new Predictions();
		for (NOAAPrediction noaaPrediction : noaaPredictions.predictions) {

			Instant noaaWhen = dtfParse.parse(noaaPrediction.t, Instant::from);
			Double noaaHeight = Double.parseDouble(noaaPrediction.v);
			boolean noaaLow = ("L".equals(noaaPrediction.type));

			predictions.addTidePoint(noaaWhen, noaaHeight, noaaLow);
		}
		
		return(predictions);
	}

	// +---------------------+
	// | NOAA API Structures |
	// +---------------------+

	public static class NOAAPrediction
	{
		public String t;
		public String v;
		public String type;
	}

	public static class NOAAPredictions
	{
		public List<NOAAPrediction> predictions;
	}

	private NOAAPredictions getNOAAPredictions(Instant when) throws Exception {

		String start = dtfUrl.format(when.minus(1, ChronoUnit.DAYS)); // ensures last extreme
		String end = dtfUrl.format(when.plus(2, ChronoUnit.DAYS)); // ensures next 2 extremes

		String url = String.format(cfg.PredictionUrlFormat,
								   start, end, cfg.StationId);

		WebRequests.Params params = new WebRequests.Params();
		WebRequests.Response response = requests.fetch(url, params);
		if (!response.successful()) response.throwException("getNOAAPredictions");

		return(new Gson().fromJson(response.Body, NOAAPredictions.class));
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;
	private DateTimeFormatter dtfUrl;
	private DateTimeFormatter dtfParse;

	private final static Logger log = Logger.getLogger(NOAA.class.getName());

	private final static double[] TIDE_MULTS = { 1.0, 2.0, 3.0, 3.0, 2.0 }; // last 1.0 is the new point

	private final static ZoneId GMT_ZONE = ZoneId.of("GMT");

	private final static String DEFAULT_PREDICTION_URL_FMT =
		"https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?" +
		"product=predictions&application=NOS.COOPS.TAC.WL&" +
		"begin_date=%s&end_date=%s&datum=MLLW&station=%s&" +
		"time_zone=gmt&units=english&interval=hilo&format=json";
}
