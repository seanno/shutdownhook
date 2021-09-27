/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.lang.Math;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Server
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public WebServer.Config Server = new WebServer.Config();
		public Tempest.Config Tempest = new Tempest.Config();

		public TempColorRange[] TempColorRanges = new TempColorRange[] {
			
			new TempColorRange(10,  176, 196, 222, 0.4),
			new TempColorRange(20,  100, 149, 237, 0.4),
			new TempColorRange(30,  65,  105, 225, 0.4),
			new TempColorRange(40,  0,   139, 139, 0.4),
			new TempColorRange(50,  46,  139, 87,  0.4),
			new TempColorRange(60,  154, 205, 50,  0.4),
			new TempColorRange(70,  255, 215, 0,   0.4),
			new TempColorRange(80,  255, 165, 0,   0.4),
			new TempColorRange(90,  255, 69,  0,   0.4),
			new TempColorRange(999, 255, 0,   0,   0.4)
		};

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public static class TempColorRange
	{
		public TempColorRange(int maxTempExclusive, int red, int green,
							  int blue, double alpha) {
			
		    MaxTempExclusive = maxTempExclusive;
		    Red = red;
		    Green = green;
		    Blue = blue;
		    Alpha = alpha;
		}
		
		public Integer MaxTempExclusive;
		
		public Integer Red = 0;
		public Integer Green = 0;
		public Integer Blue = 0;
		public Double Alpha = 1.0;
	}

	// +------------+
	// | Entrypoint |
	// +------------+

    public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat();
		
		if (args.length < 1) {
			msg("Usage: java -cp [path_to_jar] \\\n" +
				"\tcom.shutdownhook.weather.Server \\\n" +
				"\t[path_to_config] \n");
			return;
		}

		cfg = Config.fromJson(Easy.stringFromSmartyPath(args[0]));
		tempest = new Tempest(cfg.Tempest);

		try {
			server = WebServer.create(cfg.Server);
			
			registerDashboardHandler();

			String html = Easy.stringFromResource("main.html");
			server.registerStaticHandler("/", html, "text/html");
								  
			server.runSync();
		}
		finally {
			tempest.close();
		}
    }
	
	// +-------------------+
	// | Dashboard Handler |
	// +-------------------+

	private final static String DASHBOARD_TEMPLATE =
		"dashboard.html.tmpl";
	
	private final static String TKN_STATION_ID = "STATION_ID";
	private final static String TKN_STATION_NAME = "STATION_NAME";
	private final static String TKN_NOW_ICON = "NOW_ICON";
	private final static String TKN_NOW_WIND_SPEED = "NOW_WIND_SPEED";
	private final static String TKN_NOW_WIND_DIR = "NOW_WIND_DIR";
	private final static String TKN_NOW_TEMP = "NOW_TEMP";
	private final static String TKN_NOW_BGCOLOR = "NOW_BGCOLOR";

	private final static String TKN_FCAST_ITEMS = "FCAST_ITEMS";
	private final static String TKN_FCAST_COLUMN = "FCAST_COLUMN";
	private final static String TKN_FCAST_ICON = "FCAST_ICON";
	private final static String TKN_FCAST_CONDITIONS = "FCAST_CONDITIONS";
	private final static String TKN_FCAST_WIND_SPEED = "FCAST_WIND_SPEED";
	private final static String TKN_FCAST_WIND_DIR = "FCAST_WIND_DIR";
	private final static String TKN_FCAST_TEMP = "FCAST_TEMP";
	private final static String TKN_FCAST_HOUR = "FCAST_HOUR";
	private final static String TKN_FCAST_WEEKDAY = "FCAST_WEEKDAY";
	private final static String TKN_FCAST_PRECIP = "FCAST_PRECIP";

	private final static int FORECAST_ITEMS = 5;
	
	private static void registerDashboardHandler() throws Exception {

		String templateText = Easy.stringFromResource(DASHBOARD_TEMPLATE);
		final Template template = new Template(templateText);
		
		server.registerHandler("/dashboard", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String stationId = request.QueryParams.get("station");
				msg("Fetching info & forecast for station %s", stationId);
				
				final Tempest.Station station = tempest.getStation(stationId);
				final Tempest.Forecast forecast = tempest.getForecast(stationId);
				final Tempest.FormattedSnapshot current = forecast.getCurrentSnap();

				Map<String,String> tokens = new HashMap<String,String>();

				tokens.put(TKN_STATION_ID, stationId);
				tokens.put(TKN_STATION_NAME, station.stations.get(0).name);
				tokens.put(TKN_NOW_ICON, current.Icon);

				tokens.put(TKN_NOW_TEMP, Integer.toString(current.HighTempF));
				tokens.put(TKN_NOW_BGCOLOR, getTemperatureColor(current.HighTempF));
				
				tokens.put(TKN_NOW_WIND_SPEED, Integer.toString(current.WindMph));
				tokens.put(TKN_NOW_WIND_DIR, current.WindDir);

				response.setHtml(template.render(tokens, new Template.TemplateProcessor() {

					private int i = 0;
					private Tempest.FormattedSnapshot snap = null;
						
					public String token(String token, String args) throws Exception {
						
						switch (token) {
						    case TKN_FCAST_ITEMS:
								return(Integer.toString(FORECAST_ITEMS));

						    case TKN_FCAST_COLUMN:
								return(Integer.toString(i + 1));

						    case TKN_FCAST_HOUR:
								int hr = snap.Hourly.local_hour;
								String suffix = (hr < 12 ? "am" : "pm");
								if (hr > 12) hr = hr - 12;
								if (hr == 0) hr = 12;
								return(Integer.toString(hr) + suffix);

						    case TKN_FCAST_WEEKDAY:
								return(Instant
									   .ofEpochSecond(snap.Daily.day_start_local)
									   .atZone(ZoneId.of(forecast.timezone))
									   .getDayOfWeek()
									   .getDisplayName(TextStyle.FULL,
													   Locale.getDefault()));

						    case TKN_FCAST_PRECIP:
								return(Integer.toString((int)Math.round(snap.Hourly.precip_probability)) + "%");
									
						    case TKN_FCAST_ICON:
								return(snap.Icon);

						    case TKN_FCAST_CONDITIONS:
								return(snap.Conditions);

						    case TKN_FCAST_TEMP:
								return(Integer.toString(snap.HighTempF));

						    case TKN_FCAST_WIND_SPEED:
								return(Integer.toString(snap.WindMph));

						    case TKN_FCAST_WIND_DIR:
								return(snap.WindDir);
						}
						
						return("NYI");
					}

					public boolean repeat(String[] args, int counter) {

						i = counter;
						snap = (args[0].equals("D")
								? forecast.getDailySnap(i+1) /* +1 = start tomorrow */
								: forecast.getHourlySnap(i));

						return(i < FORECAST_ITEMS);
					}

				}));
			}
		});
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static String getTemperatureColor(int temp) {

		int i = 0;
		while (i < cfg.TempColorRanges.length &&
			   temp > cfg.TempColorRanges[i].MaxTempExclusive) {
			
			++i;
		}
		
		if (i == cfg.TempColorRanges.length) --i;

		return(String.format("rgba(%d,%d,%d,%f)",
							 cfg.TempColorRanges[i].Red,
							 cfg.TempColorRanges[i].Green,
							 cfg.TempColorRanges[i].Blue,
							 cfg.TempColorRanges[i].Alpha));
	}
	
	private static void msg(String fmt, Object ... args) {
		System.out.println(String.format(fmt, args));
	}

	private static Config cfg;
	private static Tempest tempest;
	private static WebServer server;
	
	private final static Logger log = Logger.getLogger(Server.class.getName());
}
