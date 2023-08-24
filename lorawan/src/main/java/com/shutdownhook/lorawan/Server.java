/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.lorawan;

import java.io.Closeable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
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
		public SqlStore.Config Sql = new SqlStore.Config();

		public String WitterMetric = "witterTankLevel";

		public String WitterGraphUrl = "/witter";

		public String WitterDataUrl = "/witterdata";
		public Integer WitterDataDaysDefault = 7;
		public String WitterDefaultTimeZone = "PST8PDT";

		public String WitterCheckUrl = "/wittercheck";
		public Double WitterWarnFeet = 3.5;
		public Double WitterCriticalFeet = 2.0;
		
		public String WitterHookUrl = "/witterhook";
		public String WitterHookUser;
		public String WitterHookPass;
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Server(Config cfg) throws Exception {
		this.cfg = cfg;
		setupWebServer();
	}

	private void setupWebServer() throws Exception {

		store = new MetricStore(cfg.Sql);
		server = WebServer.create(cfg.WebServer);

		registerWitterHook();
		registerWitterData();
		registerWitterCheck();
		registerWitterGraph();

		server.registerEmptyHandler("/favicon.ico", 404);
	}
	
	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +---------------------+
	// | registerWitterGraph |
	// +---------------------+

	private final static String GRAPH_TEMPLATE = "witterGraph.html.tmpl";
	private final static String TKN_DATA_URL = "DATA_URL";
	private final static String TKN_GRAPH_URL = "GRAPH_URL";
	private final static String TKN_DAYS = "DAYS";

	private void registerWitterGraph() throws Exception {

		String templateText = Easy.stringFromResource(GRAPH_TEMPLATE);
		final Template template = new Template(templateText);
		
		server.registerHandler(cfg.WitterGraphUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String daysStr = request.QueryParams.get("days");
				int days = (daysStr == null ? cfg.WitterDataDaysDefault : Integer.parseInt(daysStr));

				HashMap tokens = new HashMap<String,String>();
				tokens.put(TKN_DATA_URL, cfg.WitterDataUrl);
				tokens.put(TKN_GRAPH_URL, cfg.WitterGraphUrl);
				tokens.put(TKN_DAYS, Integer.toString(days));
				
				response.setHtml(template.render(tokens));
			}
		});
	}
	
	// +--------------------+
	// | registerWitterData |
	// +--------------------+

	private void registerWitterData() throws Exception {

		server.registerHandler(cfg.WitterDataUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String daysStr = request.QueryParams.get("days");
				int days = (daysStr == null ? cfg.WitterDataDaysDefault : Integer.parseInt(daysStr));

				String zoneStr = request.QueryParams.get("tz");
				ZoneId zone = ZoneId.of(zoneStr == null ? cfg.WitterDefaultTimeZone : zoneStr);

				Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
				List<MetricStore.Metric> metrics = store.getMetrics(cfg.WitterMetric, start,
																	null, null, zone);

				response.setJson(new Gson().toJson(metrics));
			}
		});
	}

	// +---------------------+
	// | registerWitterCheck |
	// +---------------------+

	private void registerWitterCheck() throws Exception {

		server.registerHandler(cfg.WitterCheckUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String zoneStr = request.QueryParams.get("tz");
				ZoneId zone = ZoneId.of(zoneStr == null ? cfg.WitterDefaultTimeZone : zoneStr);
				
				MetricStore.Metric metric =	store.getLatestMetric(cfg.WitterMetric, zone);

				String responseText = "[ERROR]";
				
				if (metric != null) {

					double feet = Convert.cmToFeet((double)metric.Value);
					if (feet < cfg.WitterCriticalFeet) {
						responseText = "[CRITICAL]";
					}
					else if (feet < cfg.WitterWarnFeet) {
						responseText = "[WARNING]";
					}
					else {
						responseText = "[OK]";
					}

					responseText = responseText + "\n" + new Gson().toJson(metric);
				}

				response.setText(responseText);
			}
		});
		
	}

	// +--------------------+
	// | registerWitterHook |
	// +--------------------+

	private void registerWitterHook() throws Exception {

		server.registerHandler(cfg.WitterHookUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				String auth = request.Headers.get("Authorization").get(0);

				String expected = "Basic " + Easy.base64Encode(cfg.WitterHookUser + ":" +
															   cfg.WitterHookPass);

				if (!expected.equals(auth)) {
					log.warning("Invalid auth: " + auth);
					response.Status = 401;
					return;
				}
				
				JsonObject msg = new JsonParser().parse(request.Body).getAsJsonObject();
				
				double waterLevel =
					msg.get("uplink_message").getAsJsonObject()
					.get("decoded_payload").getAsJsonObject()
					.get("water_level").getAsDouble();

				Instant timestamp = Instant.parse(msg.get("received_at").getAsString());
				long epochSecond = timestamp.getEpochSecond();

				log.info(String.format("Received level %02f at %s", waterLevel, timestamp));

				store.saveMetric(cfg.WitterMetric, waterLevel, epochSecond);
				response.setText("OK");
			}
		});
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private MetricStore store;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
