/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.logging.Logger;

import com.google.gson.Gson;

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

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Server(Config cfg) throws Exception {
		this.cfg = cfg;
		this.tides = new Tides(cfg.Tides);
		setupWebServer();
	}

	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerPredictionHandler();
		registerImageHandler();

		server.registerEmptyHandler("/favicon.ico", 404);
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

	// +-------------------+
	// | Single Prediction |
	// +-------------------+

	private final static String PREDICTION_TEMPLATE = "prediction.html.tmpl";
	private final static String TKN_IMG_SRC = "IMG_SRC";

	private void registerPredictionHandler() throws Exception {

		String templateText = Easy.stringFromResource(PREDICTION_TEMPLATE);
		final Template template = new Template(templateText);

		server.registerHandler(cfg.PredictionUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String dateTime = request.QueryParams.get("dateTime");
				
				Instant when = (Easy.nullOrEmpty(dateTime)
								? Instant.now() : Instant.parse(dateTime));

				Tides.TideForecast forecast = tides.forecastTide(when);

				HashMap tokens = new HashMap<String,String>();
				tokens.put(TKN_IMG_SRC, cfg.ImageUrl + "?id=" + forecast.Tide.TideId);
				
				response.setHtml(template.render(tokens));
			}
		});
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Tides tides;
	private WebServer server;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
