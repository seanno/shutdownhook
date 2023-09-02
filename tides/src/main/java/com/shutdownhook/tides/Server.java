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
		public String ImageUrl = "/image";
		public String CssUrl = "/tides.css";

		public String DateFormatToday = "h:mma";
		public String DateFormatNextSeven = "E h:mma";
		public String DateFormatSameYear = "E, MMM d, h:mma";
		public String DateFormatFallback = "M/d/y h:mma";
		
		public String LocalZone = null; // defaults to LOCAL_ZONE

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
			
		setupWebServer();
	}

	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerPredictionHandler();
		registerImageHandler();
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

	// +------------+
	// | Prediction |
	// +------------+

	// We always request predictions for six timepoints, typically:
	//
	// * Now +1 hour
	// * Now +3 hours
	// * Now +6 hours
	// * +1 day after Now at noon
	// * +2 days after Now at noon
	// * +3 days after Now at noon
	//
	// If we receive a specific dateTime query parameter, instead of "Now"
	// we start at the given timepoint as the first item, then +1 and +3 hours
	// but NOT a +6 hours reading.
	//
	// This supports in all cases two rows of three predictions, one for "nearterm"
	// and one for "midterm" values.

	private final static int PREDICTION_COLS = 3;
	
	private final static String PREDICTION_TEMPLATE = "prediction.html.tmpl";
	private final static String TKN_TIMESTAMP = "TIMESTAMP";
	private final static String TKN_PRED_ROW = "PRED_ROW";
	private final static String TKN_PRED_COL = "PRED_COL";
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

				Instant when;
				boolean includeWhen;

				if (Easy.nullOrEmpty(dateTime)) {
					when = Instant.now();
					includeWhen = false;
				}
				else {
					when = Instant.parse(dateTime);
					includeWhen = true;
				}

				List<Instant> timepoints = getForecastTimepoints(when, includeWhen);
				Tides.TideForecasts forecasts = tides.forecastTides(timepoints, 2);

				HashMap tokens = new HashMap<String,String>();
				tokens.put(TKN_TIMESTAMP, Long.toString(when.toEpochMilli()));
				tokens.put(TKN_THIS_URL, cfg.PredictionUrl);

				String html = template.render(tokens, new Template.TemplateProcessor() {

					private int iforecast;
					private Tides.TideForecast currentForecast = null;
						
					public boolean repeat(String[] args, int counter) {

						if (counter >= forecasts.Forecasts.size()) return(false);
						
						iforecast = counter;
						currentForecast = forecasts.Forecasts.get(iforecast);
						return(true);
					}

					public String token(String token, String args) throws Exception {
						
						switch (token) {
							
						    case TKN_DT:
								return(renderTokenDT(currentForecast.When, args));

							case TKN_PRED_IMG:
								return(cfg.ImageUrl + "?id=" + currentForecast.Tide.TideId);

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
		sb.append(String.format("%.1f ft", forecast.Height));

		if (forecast.Weather != null) {
			double degF = Convert.celsiusToFarenheit(forecast.Weather.air_temperature);
			sb.append(String.format(", %.0f&deg;", degF));
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

	private List<Instant> getForecastTimepoints(Instant when, boolean includeWhen) {

		List<Instant> timepoints = new ArrayList<Instant>();

		// near-term
		if (includeWhen) timepoints.add(when);
		timepoints.add(when.plus(1, ChronoUnit.HOURS));
		timepoints.add(when.plus(3, ChronoUnit.HOURS));
		if (!includeWhen) timepoints.add(when.plus(6, ChronoUnit.HOURS));

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
