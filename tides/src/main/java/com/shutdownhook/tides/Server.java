/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();
		public Tides.Config Tides = new Tides.Config();

		public String PredictionUrl = "/predict";
		public String TestUrl = "/test";
		public String ImageUrl = "/image";
		public String CssUrl = "/tides.css";

		public String DateFormatToday = "h:mma";
		public String DateFormatNextSeven = "E h:mma";
		public String DateFormatSameYear = "E, MMM d, h:mma";
		public String DateFormatFallback = "M/d/y h:mma";
		
		public String LocalZone = null; // defaults to LOCAL_ZONE

		public boolean useExternalImageUrl() {
			return(Tides.Store.ImageUrlPrefix != null);
		}
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Server(Config cfg) throws Exception {
		this.cfg = cfg;
		this.tides = new Tides(cfg.Tides);
		
		this.dtfToday = DateTimeFormatter.ofPattern(cfg.DateFormatToday);
		this.dtfNextSeven = DateTimeFormatter.ofPattern(cfg.DateFormatNextSeven);
		this.dtfSameYear = DateTimeFormatter.ofPattern(cfg.DateFormatSameYear);
		this.dtfFallback = DateTimeFormatter.ofPattern(cfg.DateFormatFallback);
		
		this.zone = (cfg.LocalZone == null ? ZoneId.systemDefault()
					 : ZoneId.of(cfg.LocalZone));

		log.info(String.format("Using timezone %s (from %s)",
							   this.zone, cfg.LocalZone));
			
		setupWebServer();
	}

	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerPredictionHandler();
		registerTestHandler();
		if (!cfg.useExternalImageUrl()) registerImageHandler();
		registerStaticHandlers();
	}
	
	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +-------+
	// | Image |
	// +-------+

	private void registerImageHandler() {
		server.registerHandler(cfg.ImageUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				response.Status = 404; // pessimist
				
				String id = request.QueryParams.get("id");
				if (id == null) return;

				TideStore.Tide tide = tides.getTide(id);
				if (tide == null) return;

				response.Status = 200;
				response.ContentType = "image/jpeg";
				response.BodyFile = tide.ImageFile;
			}
		});
	}

	// +------+
	// | Test |
	// +------+
	
	private final static String TEST_TEMPLATE = "test.html.tmpl";
	private final static String TEST_TKN_REF_IMG = "REF_IMG";
	private final static String TEST_TKN_PRED_IMG = "PRED_IMG";
	private final static String TEST_TKN_COMP = "COMPARISON";

	private void registerTestHandler() throws Exception {

		String templateText = Easy.stringFromResource(TEST_TEMPLATE);
		final Template template = new Template(templateText);

		server.registerHandler(cfg.TestUrl, new WebServer.Handler() {
						
			public void handle(Request request, Response response) throws Exception {

				String dateTime = request.QueryParams.get("dateTime");

				Instant when = (Easy.nullOrEmpty(dateTime)
								? Instant.now()
								: Instant.parse(dateTime));

				when = when.atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant();
				
				List<Tides.PredictionTest> tests = tides.testPredictions(when);

				HashMap tokens = new HashMap<String,String>();
				tokens.put(TKN_TIMESTAMP, Long.toString(when.toEpochMilli()));
				tokens.put(TKN_THIS_URL, cfg.PredictionUrl);

				String html = template.render(tokens, new Template.TemplateProcessor() {

					private Tides.PredictionTest currentTest = null;
						
					public boolean repeat(String[] args, int counter) {

						if (counter == tests.size()) return(false);
						currentTest = tests.get(counter);
						return(true);
					}

					public String token(String token, String args) throws Exception {
						
						switch (token) {

							case TEST_TKN_REF_IMG:
								return(renderTokenImage(currentTest.Reference));
								
							case TEST_TKN_PRED_IMG:
								return(renderTokenImage(currentTest.Forecast));
								
							case TEST_TKN_COMP:
								return(renderTokenCompare(currentTest));
						}

						log.warning("UNKNOWN TOKEN: " + token);
						return("");
					}
				});
				
				response.setHtml(html);
			}
		});
	}

	private String renderTokenCompare(Tides.PredictionTest test) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("<table class=\"compare\">");
		sb.append("<tr><th>&nbsp;</th><th>Reference</th><th>Prediction</th><th>Diff</th></tr>");

		TideStore.Tide tref = test.Reference;
		TideStore.Tide tpred = test.Forecast.Tide;

		sb.append(compareRowDbl("Height", tref.TideHeight, tpred.TideHeight));
		sb.append(compareRowInt("DOY", tref.DayOfYear, tpred.DayOfYear));
		sb.append(compareRowInt("MOD", tref.MinuteOfDay, tpred.MinuteOfDay));

		Weather.Metrics mref = tref.WeatherMetrics;
		Weather.Metrics mpred = tpred.WeatherMetrics;

		if (mref != null && mpred != null) {
			sb.append(compareRowDbl("UV", mref.UV, mpred.UV));
			sb.append(compareRowDbl("PressureMb", mref.PressureMb, mpred.PressureMb));
			sb.append(compareRowDbl("WindMps", mref.WindMps, mpred.WindMps));
			sb.append(compareRowDbl("Humidity", mref.Humidity, mpred.Humidity));
		}

		sb.append("</table>");
		return(sb.toString());
	}

	private String compareRowInt(String hdr, int ref, int pred) {
		
		int diff = ref - pred; if (diff < 0) diff *= -1;
		return(String.format("<tr><th>%s</th><td>%d</td><td>%d</td><td>%d</td></tr>",
							 hdr, ref, pred, diff));
	}

	private String compareRowDbl(String hdr, double ref, double pred) {
		
		double diff = ref - pred; if (diff < 0) diff *= -1.0;
		return(String.format("<tr><th>%s</th><td>%.1f</td><td>%.1f</td><td>%.1f</td></tr>",
							 hdr, ref, pred, diff));
	}
	
	// +------------+
	// | Prediction |
	// +------------+

	// We always request predictions for seve timepoints:
	//
	// * When (Provided DateTime or Now)
	// * When +1 hour
	// * When +3 hours
	// * When +6 hours
	// * +1 day after Now at noon
	// * +2 days after Now at noon
	// * +3 days after Now at noon
	//
	// This supports a header row for when, then two rows of three predictions,
	// one for "near-term" and one for "mid-term" values.

	private final static int PREDICTION_COLS = 3;
	
	private final static String PREDICTION_TEMPLATE = "prediction.html.tmpl";
	private final static String TKN_KEY_TIME = "KEY_TIME";
	private final static String TKN_KEY_METRICS = "KEY_METRICS";
	private final static String TKN_EXTREMES = "EXTREMES";
	private final static String TKN_TIMESTAMP = "TIMESTAMP";
	private final static String TKN_PRED_ROW = "PRED_ROW";
	private final static String TKN_PRED_COL = "PRED_COL";
	private final static String TKN_PRED_DT = "PRED_DT";
	private final static String TKN_PRED_IMG = "PRED_IMG";
	private final static String TKN_PRED_TIME = "PRED_TIME";
	private final static String TKN_PRED_METRICS = "PRED_METRICS";
	private final static String TKN_THIS_URL = "THIS_URL";
	private final static String TKN_DT = "DT";

	private void registerPredictionHandler() throws Exception {

		String templateText = Easy.stringFromResource(PREDICTION_TEMPLATE);
		final Template template = new Template(templateText);

		server.registerHandler(cfg.PredictionUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String dateTime = request.QueryParams.get("dateTime");

				Instant when = (Easy.nullOrEmpty(dateTime)
								? Instant.now()
								: Instant.parse(dateTime));
				
				List<Instant> timepoints = getForecastTimepoints(when);
				Tides.TideForecasts forecasts = tides.forecastTides(timepoints, 2);

				HashMap tokens = new HashMap<String,String>();
				tokens.put(TKN_TIMESTAMP, Long.toString(when.toEpochMilli()));
				tokens.put(TKN_THIS_URL, cfg.PredictionUrl);

				String html = template.render(tokens, new Template.TemplateProcessor() {

					private int iforecast;
					private Tides.TideForecast currentForecast = null;
						
					public boolean repeat(String[] args, int counter) {

						// -1 because index 0 isn't part of the :rpt
						if (counter >= (forecasts.Forecasts.size() - 1)) return(false);
						
						iforecast = counter; 
						currentForecast = forecasts.Forecasts.get(iforecast + 1);
						return(true);
					}

					public String token(String token, String args) throws Exception {
						
						switch (token) {

							case TKN_EXTREMES:
								return(renderTokenExtremes(forecasts.Extremes));
								
						    case TKN_DT:
								return(renderTokenDT(currentForecast.When, args));

							case TKN_KEY_TIME:
								return(renderTokenTime(forecasts.Forecasts.get(0)));

							case TKN_KEY_METRICS:
								return(renderTokenMetrics(forecasts.Forecasts.get(0)));

							case TKN_PRED_IMG:
								return(renderTokenImage(currentForecast));

							case TKN_PRED_DT:
								return(currentForecast.When.toString());

							case TKN_PRED_TIME:
								return(renderTokenTime(currentForecast));

							case TKN_PRED_METRICS:
								return(renderTokenMetrics(currentForecast));

							case TKN_PRED_COL:
								return(Integer.toString((iforecast % PREDICTION_COLS) + 1));

							case TKN_PRED_ROW:
								return(Integer.toString((iforecast / PREDICTION_COLS) + 1));
						}

						log.warning("UNKNOWN TOKEN: " + token);
						return("");
					}
				});
				
				response.setHtml(html);
			}
		});
	}


	private String renderTokenExtremes(List<Tides.TideExtreme> extremes) {

		Tides.TideExtreme ex1 = extremes.get(0);
		String dt1 = ex1.Forecast.When.atZone(zone).format(dtfToday);
		dt1 = dt1.replace("AM", "am").replace("PM", "pm");
			
		Tides.TideExtreme ex2 = extremes.get(1);
		String dt2 = ex2.Forecast.When.atZone(zone).format(dtfToday);
		dt2 = dt2.replace("AM", "am").replace("PM", "pm");
		
		return(String.format("%s %s %.1f ft &middot; %s %s %.1f ft",
							 ex1.Type.toHTML(), dt1, ex1.Forecast.Height,
							 ex2.Type.toHTML(), dt2, ex2.Forecast.Height));
	}
	
 	private String renderTokenImage(Tides.TideForecast forecast) throws Exception {
		return(renderTokenImage(forecast.Tide));
	}

	private String renderTokenImage(TideStore.Tide tide) throws Exception {
		return(cfg.useExternalImageUrl()
			   ? tides.getTideUrl(tide)
			   : cfg.ImageUrl + "?id=" + tide.TideId);
	}
	
	private String renderTokenTime(Tides.TideForecast forecast) {

		ZonedDateTime now = ZonedDateTime.now(zone);
		int nowYear = now.getYear();
		int nowDayOfYear = now.get(ChronoField.DAY_OF_YEAR);
		
		int plusSevenDayOfYear = now.plus(7, ChronoUnit.DAYS)
			                        .get(ChronoField.DAY_OF_YEAR);
		
		ZonedDateTime disp = forecast.When.atZone(zone);
		int dispYear = disp.getYear();
		int dispDayOfYear = disp.get(ChronoField.DAY_OF_YEAR);

		DateTimeFormatter dtf = dtfFallback;
	   
		if (nowYear == dispYear) {

			if (nowDayOfYear == dispDayOfYear) {
				// today
				dtf = dtfToday;
			}
			else if (dispDayOfYear > nowDayOfYear &&
					 dispDayOfYear < plusSevenDayOfYear) {
				// in next 7 days
				dtf = dtfNextSeven;
			}
			else {
				// just this year
				dtf = dtfSameYear;
			}
		}

		return(disp.format(dtf).replace("AM","am").replace("PM","pm"));
	}

	private String renderTokenMetrics(Tides.TideForecast forecast) {

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%.1f ft %s", forecast.Height,
								forecast.PredictionType.toHTML()));

		if (forecast.WeatherMetrics != null) {
			sb.append(String.format(" %.0f&deg;", forecast.WeatherMetrics.TempF));
		}
		
		return(sb.toString());
	}

	private String renderTokenDT(Instant whenBase, String args) {
		
		String[] flds = args.split(" ");
		int diff = Integer.parseInt(flds[0].trim());
								
		ChronoUnit unit = (flds[1].trim().toLowerCase().equals("d")
						   ? ChronoUnit.DAYS : ChronoUnit.HOURS);

		Instant whenNew = (diff < 0 ? whenBase.minus(diff * -1, unit)
						   : whenBase.plus(diff, unit));

		return(whenNew.toString());
	}

	private List<Instant> getForecastTimepoints(Instant when) {

		List<Instant> timepoints = new ArrayList<Instant>();

		// near-term
		timepoints.add(when);
		timepoints.add(when.plus(1, ChronoUnit.HOURS));
		timepoints.add(when.plus(3, ChronoUnit.HOURS));
		timepoints.add(when.plus(6, ChronoUnit.HOURS));

		// mid-term
		Instant midTerm = when
			.atZone(zone)
			.truncatedTo(ChronoUnit.DAYS)
			.plus(36, ChronoUnit.HOURS)
			.toInstant();

		timepoints.add(midTerm);
		timepoints.add(midTerm.plus(1, ChronoUnit.DAYS));
		timepoints.add(midTerm.plus(2, ChronoUnit.DAYS));

		return(timepoints);
	}

	// +--------+
	// | Static |
	// +--------+

	private final static String CSS_TEMPLATE = "tides.css";
	
	private void registerStaticHandlers() throws Exception {

		String cssText = Easy.stringFromResource(CSS_TEMPLATE);
		server.registerStaticHandler(cfg.CssUrl, cssText, "text/css");
		
		server.registerEmptyHandler("/favicon.ico", 404);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Tides tides;
	private WebServer server;
	
	private DateTimeFormatter dtfToday;
	private DateTimeFormatter dtfNextSeven;
	private DateTimeFormatter dtfSameYear;
	private DateTimeFormatter dtfFallback;
	private ZoneId zone;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
