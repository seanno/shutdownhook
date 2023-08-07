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
		POINT,
		EST
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

	public static class Predictions
	{
		public Predictions() {
			this.predictions = new ArrayList<Prediction>();
		}

		// +-----------------+
		// | getNextExtremes |
		// +-----------------+
		
		public Predictions nextExtremes() throws IllegalArgumentException {
			return(nextExtremes(Instant.now()));
		}

		public Predictions nextExtremes(Instant when) throws IllegalArgumentException {
			
			int pos = binarySearch(when);
			if (pos < 0) pos = (-pos) + 1;

			Predictions extremes = new Predictions();
			for (int i = pos; i < predictions.size(); ++i) {
				Prediction p = predictions.get(i);
				PredictionType ptype = p.PredictionType;
				if (ptype == PredictionType.LOW || ptype == PredictionType.HIGH) {
					extremes.predictions.add(p);
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
			if (pos >= 0) return(predictions.get(pos));

			// calculations below will always be valid if array is sorted
			// (which we control) and the tests in binarySearch lets us get here

			int insertionPoint = (-pos) - 1;
			Prediction pLow = predictions.get(insertionPoint - 1);
			Prediction pHigh = predictions.get(insertionPoint);

			// fraction of time through period
			double timeWhole = (double) pLow.Time.until(pHigh.Time, ChronoUnit.MILLIS);
			double timeFraction = ((double) pLow.Time.until(when, ChronoUnit.MILLIS)) / timeWhole;

			// tide change through period (may be - if going down, that's ok)
			double tideWhole = (pHigh.Height - pLow.Height);
				
			// and interpolate!
			double estimate = pLow.Height + (tideWhole * timeFraction);

			Prediction pEst = new Prediction();
			pEst.Time = when;
			pEst.Height = estimate;
			pEst.PredictionType = PredictionType.EST;
			return(pEst);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < predictions.size(); ++i) {
				sb.append(String.format("%d\t%s\n", i, predictions.get(i)));
			}
			return(sb.toString());
		}

		private int binarySearch(Instant when) throws IllegalArgumentException {
			
			if (predictions.size() == 0 ||
				when.isBefore(predictions.get(0).Time) ||
				when.isAfter(predictions.get(predictions.size() - 1).Time)) {

				throw new IllegalArgumentException("outside predictions range");
			}

			// same semantics as Collections.binarySearch
			Prediction pKey = new Prediction(); pKey.Time = when;
			return(Collections.binarySearch(predictions, pKey));
		}

		// must be called for alternating H/L points in ascending time order.
		// uses the "rule of 12" to add intermediate tide points between extremes
	    private void addTidePoint(Instant when, double height, boolean low) {
			addTwelfths(when, height);
			addOne(when, height, (low ? PredictionType.LOW : PredictionType.HIGH));
		}

		private void addTwelfths(Instant when, double height) {
			
			if (predictions.size() == 0) return;
			
			Prediction pLast = predictions.get(predictions.size() - 1);

			// note this can be positive or negative which is what we want
			double heightTwelfth = (height - pLast.Height) / 12.0;

			long secsBetween =
				Duration.between(pLast.Time, when).getSeconds()
				/ (TIDE_MULTS.length + 1);
												   
			Instant interWhen = pLast.Time;
			double interHeight = pLast.Height;

			for (double mult : TIDE_MULTS) {
				interWhen = interWhen.plusSeconds(secsBetween);
				interHeight += (heightTwelfth * mult);
				addOne(interWhen, interHeight, PredictionType.POINT);
			}
		}

		private void addOne(Instant when, double height,
							PredictionType predictionType) {

			Prediction p = new Prediction();
			p.Time = when;
			p.Height = height;
			p.PredictionType = predictionType;
			
			predictions.add(p);
		}
		
		private List<Prediction> predictions;
		
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
