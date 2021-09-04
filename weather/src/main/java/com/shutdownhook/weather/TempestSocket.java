/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.io.Closeable;
import java.lang.Runtime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.TextWebSocket;
import com.shutdownhook.toolbox.Worker;

public class TempestSocket implements Closeable, TextWebSocket.Receiver
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public String BaseUrl = "wss://ws.weatherflow.com/swd/data";
		public TextWebSocket.Config TextWebSocketConfig = new TextWebSocket.Config();
		public Integer ActivityStallSeconds = 300;
		public Integer SupervisorSleepSeconds = 5;
		public Integer SupervisorShutdownWaitSeconds = 10;
	}

	public TempestSocket(Config cfg, String deviceId, String deviceToken,
						 ObservationReceiver receiver) throws Exception {

		this.cfg = cfg;
		this.deviceId = deviceId;
		this.deviceToken = deviceToken;
		this.receiver = receiver;
		this.jsonParser = new JsonParser();
		
		this.lastActivity = null;
		this.tws = null;
		
		startSupervisor();
	}

	public void close() {
		closeSocket();
		stopSupervisor();
	}

	// +-------------------+
	// | Supervisor Thread |
	// +-------------------+

	private void startSupervisor() {

		supervisor = new Worker() {

			public void work() throws Exception {
				try {
					log.info(String.format("Starting supervisor thread for device " + deviceId));
					
					while (!sleepyStop(cfg.SupervisorSleepSeconds)) {
						
						Instant minActivity =
							Instant.now().minus(cfg.ActivityStallSeconds, ChronoUnit.SECONDS);

						if (lastActivity == null || lastActivity.isBefore(minActivity)) {
							try {
								resetSocket();
							}
							catch (Exception e1) {
								touchLastActivity();
								log.severe("Exception in resetSocket; will retry after delay (" +
										   e1.toString() + ")");
							}
						}
					}
				}
				catch (Exception e) {
					log.severe("Exception in supervisor thread; exiting: " + e.toString());
				}
			}

			public void cleanup(Exception e) {
				// nut-n-honey
			}
	
		};

		supervisor.start();
	}

	private void stopSupervisor() {
		supervisor.signalStop();
		if (!supervisor.waitForStop(cfg.SupervisorShutdownWaitSeconds)) {
			log.severe("Supervisor thread refused to stop for device " + deviceId);
		}
	}

	// +-----------------------+
	// | Socket (Synchronized) |
	// +-----------------------+

	private synchronized void touchLastActivity() {
		lastActivity = Instant.now();
	}
	
	private synchronized void resetSocket() throws Exception {

		closeSocket();
		
		String url = cfg.BaseUrl + "?token=" + deviceToken;
		
		lastActivity = Instant.now();
		tws = new TextWebSocket(url, cfg.TextWebSocketConfig, this);

		String startMsg = String.format(START_MSG_FMT, deviceId, new Random().nextInt(20000));
		tws.send(startMsg);
	}

	private synchronized void closeSocket() {

		if (tws != null) {
			
			try {
				String stopMsg = String.format(STOP_MSG_FMT, deviceId, new Random().nextInt(20000));
				tws.send(stopMsg);
			}
			catch (Exception e) {
				// eat it
			}
			
			tws.close();
			tws = null;
		}
	}
	
	// +---------------------+
	// | ObservationReceiver |
	// +---------------------+

	public interface ObservationReceiver {
		default public void precipitationStart(String deviceId) throws Exception { }
		default public void lightningStrike(String deviceId, StrikeData data) throws Exception { }
		default public void observation(String deviceId, ObsData data) throws Exception { }
	}

	public static class StrikeData
	{
		public StrikeData(JsonObject json) {
			JsonArray evt = json.getAsJsonArray("evt");
			this.EpochTime = evt.get(0).getAsLong();
			this.DistanceKm = evt.get(1).getAsDouble();
		}
		
		public Long EpochTime;
		public Double DistanceKm;
	}

	public static class ObsData
	{
		public ObsData(JsonArray obs) {
			this.EpochTime = obs.get(0).getAsLong();

			this.WindMinMps = obs.get(1).getAsDouble();
			this.WindAvgMps = obs.get(2).getAsDouble();
			this.WindMaxMps = obs.get(3).getAsDouble();
			this.WindDirectionDegrees = obs.get(4).getAsInt();
			this.WindIntervalSeconds = obs.get(5).getAsInt();
			
			this.StationPressureMb = obs.get(6).getAsDouble();
			this.AirTempCelsius = obs.get(7).getAsDouble();
			this.RelativeHumidityPct = obs.get(8).getAsDouble();

			this.IlluminanceLux = obs.get(9).getAsDouble();
			this.UVIndex = obs.get(10).getAsDouble();
			this.SolarRadWpm2 = obs.get(11).getAsDouble();

			this.RainMm = obs.get(12).getAsDouble();
			this.PrecipitationType = obs.get(13).getAsInt();

			this.LightningAvgDistanceKm = obs.get(14).getAsDouble();
			this.LightningStrikeCount = obs.get(15).getAsInt();

			this.BatteryVolts = obs.get(16).getAsDouble();
			this.ReportIntervalMinutes = obs.get(17).getAsInt();
		}

		public Long EpochTime;
		public Integer ReportIntervalMinutes;
		
		public Double WindMinMps;
		public Double WindAvgMps;
		public Double WindMaxMps;
		public Integer WindDirectionDegrees;
		public Integer WindIntervalSeconds;

		public Double StationPressureMb;
		public Double AirTempCelsius;
		public Double RelativeHumidityPct;

		public Double IlluminanceLux;
		public Double UVIndex;
		public Double SolarRadWpm2;

		public Double RainMm;
		public Integer PrecipitationType;

		public Double LightningAvgDistanceKm;
		public Integer LightningStrikeCount;

		public Double BatteryVolts;

		public boolean isRaining() { return((PrecipitationType & 1) != 0); }
		public boolean isHailing() { return((PrecipitationType & 2) != 0); }
	}
	
	// +------------------------+
	// | TextWebSocket.Receiver |
	// +------------------------+

	public void receive(String message, TextWebSocket tws) throws Exception {

		lastActivity = Instant.now();
		
		JsonObject json = jsonParser.parse(message).getAsJsonObject();
		String type = json.getAsJsonPrimitive("type").getAsString();
		
		switch (type) {
		    case "evt_precip":
				receiver.precipitationStart(deviceId);
				break;

		    case "evt_strike":
				receiver.lightningStrike(deviceId, new StrikeData(json));
				break;

		    case "obs_st":
				// "obs" is an array of arrays ... I have never seen > 1 element
				// in this list but better safe than sorry I guess...
				JsonArray observations = json.getAsJsonArray("obs");
				for (int i = 0; i < observations.size(); ++i) {
					JsonArray obs = (JsonArray) observations.get(i);
					receiver.observation(deviceId, new ObsData(obs));
				}
				break;

		    default:
				// just eat it
				break;
		}
	}
	
	public void error(Throwable error, TextWebSocket tws) throws Throwable {

		log.warning("WebSocket error; will reset: " + error.toString());
		lastActivity = null;
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static final String START_MSG_FMT =
		"{ 'type': 'listen_start', 'device_id': %s,'id': '%d' }";
	
	private static final String STOP_MSG_FMT =
		"{ 'type': 'listen_stop', 'device_id': %s,'id': '%d' }";

	private Config cfg;
	private String deviceId;
	private String deviceToken;
	private ObservationReceiver receiver;
	private JsonParser jsonParser;
	private TextWebSocket tws;
	private Instant lastActivity;
	private Worker supervisor;
	
	private final static Logger log = Logger.getLogger(TempestSocket.class.getName());

	// +------+
	// | main |
	// +------+

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {

			System.out.println("Usage: java -cp [path_to_jar] \\\n" +
							   "\tcom.shutdownhook.weather.TempestSocket \\\n" +
							   "\t[device_id,access_token]+ ");
			return;
		}

		final Gson gson = new GsonBuilder().setPrettyPrinting().create();
		final Object shutdownTrigger = new Object();
		final List<TempestSocket> sockets = new ArrayList<TempestSocket>();

		for (int i = 0; i < args.length; ++i) {

			Config cfg = new Config();
			// cfg.TextWebSocketConfig.LogContent = true;

			String[] flds = args[i].split(",");

			sockets.add(new TempestSocket(cfg, flds[0], flds[1], new ObservationReceiver() {
					
			    public void precipitationStart(String deviceId) throws Exception {
					System.out.println(String.format("''' [%6s] Precipitation started", deviceId));
				}
				
			    public void lightningStrike(String deviceId, StrikeData data) throws Exception {
					System.out.println(String.format("/// [%6s] Lightning strike\n%s",
													 deviceId, gson.toJson(data)));
				}
				
				public void observation(String deviceId, ObsData data) throws Exception {
					System.out.println(String.format("??? [%6s] Observation\n%s",
													 deviceId, gson.toJson(data)));
				}
			}));
					
			System.out.println("+++ Added device " + flds[0]);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				for (TempestSocket socket : sockets) socket.close();
			}
			catch (Exception e) {
				// nothihg
			}
			finally {
				synchronized (shutdownTrigger) { shutdownTrigger.notify(); }
			}
		}));

		synchronized (shutdownTrigger) {
			try { shutdownTrigger.wait(); }
			catch (InterruptedException e) { /* nut-n-honey */ }
		}
	}
}
