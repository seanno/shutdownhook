/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
			server.runSync();
		}
		finally {
			tempest.close();
		}
    }
	
	// +----------+
	// | Handlers |
	// +----------+

	private final static String DASHBOARD_TEMPLATE =
		"dashboard.html.tmpl";
	
	private final static String ICON_URL_FMT =
		"https://s3.amazonaws.com/tempest.cdn/assets/better-forecast/v4/%s.svg";

	private final static String TKN_STATION_ID = "{{STATION_ID}}";
	private final static String TKN_STATION_NAME = "{{STATION_NAME}}";
	private final static String TKN_NOW_ICON = "{{NOW_ICON}}";
	private final static String TKN_NOW_WIND_SPEED = "{{NOW_WIND_SPEED}}";
	private final static String TKN_NOW_WIND_DIR = "{{NOW_WIND_DIR}}";
	private final static String TKN_NOW_TEMP = "{{NOW_TEMP}}";
	private final static String TKN_NOW_BGCOLOR = "{{NOW_BGCOLOR}}";
	
	private static void registerDashboardHandler() throws Exception {

		String templateText = Easy.stringFromResource(DASHBOARD_TEMPLATE);
		final Template template = new Template(templateText);
		
		server.registerHandler("/dashboard", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String stationId = request.QueryParams.get("station");
				msg("Fetching info & forecast for station %s", stationId);
				Tempest.Station station = tempest.getStation(stationId);
				Tempest.Forecast forecast = tempest.getForecast(stationId);

				Map<String,String> tokens = new HashMap<String,String>();

				tokens.put(TKN_STATION_ID, stationId);
				tokens.put(TKN_STATION_NAME, station.stations.get(0).name);
				
				// Temperature (F)
				int temp = (int) Math.round(
								     Convert.celsiusToFarenheit(
								     forecast.current_conditions.air_temperature));

				String overrideTemp = request.QueryParams.get("tf");
				if (overrideTemp != null) temp = Integer.parseInt(overrideTemp);

				tokens.put(TKN_NOW_TEMP, Integer.toString(temp));
				tokens.put(TKN_NOW_BGCOLOR, getTemperatureColor(temp));
				
				// Wind (MPH)
				int wind = (int) Math.round(
									 Convert.metersToMiles(
									 forecast.current_conditions.wind_avg)
									 * 60 * 60);

				tokens.put(TKN_NOW_WIND_SPEED, Integer.toString(wind));

				tokens.put(TKN_NOW_WIND_DIR,
						   wind > 0 ? forecast.current_conditions.wind_direction_cardinal : "");

				response.setHtml(template.render(tokens));
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