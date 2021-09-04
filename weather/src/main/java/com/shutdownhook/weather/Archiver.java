/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.weather;

import java.lang.InterruptedException;
import java.lang.Runtime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Convert;
import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.SqlStore;
import com.shutdownhook.toolbox.WebRequests;
import com.shutdownhook.toolbox.Worker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Archiver
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public SqlStore.Config Sql;
		public TempestSocket.Config Socket = new TempestSocket.Config();
		public Tempest.Config Tempest = new Tempest.Config();

		public Integer MinAggCoveragePct = 90; 

		public Integer PruneHistoryDays = 90; // 0 to never prune readings
		public Integer PruneSleepSeconds = (60 * 60);
		public Integer PruneShutdownWaitSeconds = 10; 
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

    public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat();
		
		if (args.length < 2) {
			usage();
			return;
		}

		cfg = Config.fromJson(Easy.stringFromSmartyPath(args[0]));
		store = new WeatherStore(new SqlStore(cfg.Sql));

		switch (args[1].trim().toLowerCase()) {
			
	        case "list":
				list();
				break;

            case "add":
				add(args[2], args[3]);
				break;

	        case "daemon":
				daemon();
				break;

            default:
				usage();
				break;
		}
    }
	
	private static void usage() {
		msg("Usage: java -cp [path_to_jar] \\\n" +
			"\tcom.shutdownhook.weather.Archiver \\\n" +
			"\t[path_to_config] \\\n" +
			"\t[action] [params]*\n ");
		
		msg("List Stations: list");
		msg("Add Station: add [station_id] [access_token]");
		msg("Start archival daemon: daemon");
		msg("Current observations: obs [station_id]");
		msg("Forecast: forecast [station_id]");
	}
	
	// +-------+
	// | Admin |
	// +-------+

	private static void list() throws Exception {
		for (WeatherStore.Station station : store.listStations()) {
			msg(station.toString());
		}
	}

	private static void add(String stationId, String accessToken) throws Exception {

		Tempest tempest = null;

		try {
			Tempest.StationConfig stationConfig = new Tempest.StationConfig();
			stationConfig.StationId = stationId;
			stationConfig.AccessToken = accessToken;
		
			cfg.Tempest.Stations.add(stationConfig);
		
			tempest = new Tempest(cfg.Tempest);

			Tempest.Station tempestStation = tempest.getStation(stationId);

			WeatherStore.Station storeStation = new WeatherStore.Station();
			storeStation.StationId = stationId;
			storeStation.AccessToken = accessToken;

			for (Tempest.Location loc : tempestStation.stations) {
				for (Tempest.Device dev : loc.devices) {
					if ("ST".equals(dev.device_type)) {
						if (storeStation.DeviceId != null) {
							throw new Exception("Found multiple ST (Tempest) devices for station");
						}

						storeStation.DeviceId = dev.device_id;
						storeStation.Latitude = loc.latitude;
						storeStation.Longitude = loc.longitude;

						storeStation.Name = (loc.name == null ? loc.public_name : loc.name);
						if (storeStation.Name == null) storeStation.Name = stationId;
					}
				}
			}

			store.addStation(storeStation);

			msg("Added station\n");
			msg(storeStation.toString());
		}
		finally {
			if (tempest != null) tempest.close();
		}
	}

	// +---------+
	// | Queries |
	// +---------+

	private static void obs(String stationId) throws Exception {
	}
	
	private static void forecast(String stationId) throws Exception {
	}

	// +--------+
	// | Daemon |
	// +--------+

	public static class AggState
	{
		public AggState(ChronoUnit truncUnit) { this.TruncUnit = truncUnit; }
		public ChronoUnit TruncUnit;
		public Instant EpochStart;
	}
	
	public static class StationState
	{
		public StationState(WeatherStore.Station station) {
			Station = station;
			AggStates = new ArrayList<AggState>();
			AggStates.add(new AggState(ChronoUnit.HOURS));
 			AggStates.add(new AggState(ChronoUnit.DAYS));
		}
		
		public WeatherStore.Station Station;
		public TempestSocket Socket;
		public List<AggState> AggStates;
	}
	
	private static void daemon() throws Exception {

		final Map<String,StationState> stationMap = startListening();
		final Worker pruner = startPruning();
		final Object shutdownTrigger = new Object();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try { stopPruning(pruner); stopListening(stationMap); }
			catch (Exception e) { /* nut-n-honey */ }
			finally { synchronized (shutdownTrigger) { shutdownTrigger.notify(); } }
		}));
		
		msg("Listening for observations; ^C to exit");

		synchronized (shutdownTrigger) {
			try { shutdownTrigger.wait(); }
			catch (InterruptedException e) { /* nut-n-honey */ }
		}
	}

	private static void stopListening(Map<String,StationState> stationMap) {
		for (StationState stationState : stationMap.values()) {
			stationState.Socket.close();
		}
	}

	private static Map<String,StationState> startListening() throws Exception {

		final Map<String,StationState> stationMap = new HashMap<String,StationState>();
		
		for (WeatherStore.Station station : store.listStations()) {
			
			msg("Monitoring station %s (id = %s)", station.Name, station.StationId);

			StationState stationState = new StationState(station);
			stationMap.put(station.DeviceId, stationState);

			stationState.Socket = new TempestSocket(cfg.Socket, station.DeviceId, station.AccessToken,
													new TempestSocket.ObservationReceiver() {
									  
		        public void observation(String deviceId, TempestSocket.ObsData data) throws Exception {

					StationState stationState = stationMap.get(deviceId);
					Instant dateEpoch = Instant.ofEpochSecond(data.EpochTime);
				
					WeatherStore.Metrics m = new WeatherStore.Metrics();
					m.StationId = stationState.Station.StationId;
					m.EpochTime = dateEpoch;
					m.SpanMinutes = data.ReportIntervalMinutes;
					m.TempF = Convert.celsiusToFarenheit(data.AirTempCelsius);
					m.PressureMb = data.StationPressureMb;
					m.HumidityPct = data.RelativeHumidityPct;
					m.PrecipIn = Convert.millimetersToInches(data.RainMm);
					m.WindMph = (Convert.metersToMiles(data.WindAvgMps) * 60 * 60);
					m.WindMaxMph = (Convert.metersToMiles(data.WindMaxMps) * 60 * 60);
					m.SolarRadWpm2 = data.SolarRadWpm2;
					
					msg("??? [%6s] Observation:\t%s\t%f\t%f\t%f",
						m.StationId, m.EpochTime, m.TempF, m.PressureMb, m.WindMph);

					store.addMetrics(m);

					for (AggState aggState : stationState.AggStates) {
						checkAggregate(stationState.Station.StationId, dateEpoch, aggState);
					}
				}

			    private void checkAggregate(String stationId, Instant dateEpoch, AggState aggState) throws Exception {

					Instant epochAgg = dateEpoch.truncatedTo(aggState.TruncUnit);

					if (!epochAgg.equals(aggState.EpochStart)) {

						if (aggState.EpochStart != null) {

							WeatherStore.Metrics m =
								store.computeAggregateMetrics(stationId,
															  aggState.EpochStart,
															  (int) aggState.TruncUnit.getDuration().toMinutes(),
															  cfg.MinAggCoveragePct);

							if (m == null) {
								log.info(String.format("Station %s; Insufficient coverage for %s agg",
													   stationId, aggState.TruncUnit));
							}
							else {
								msg("??? [%6s] AGG %s:\t%s\t%f\t%f\t%f",
									m.StationId, aggState.TruncUnit, m.EpochTime,
									m.TempF, m.PressureMb, m.WindMph);

								store.addMetrics(m);
							}
						}

						aggState.EpochStart = epochAgg;
					}
				}
		    });
		}

		return(stationMap);
	}

	// +----------------+
	// | Pruning Thread |
	// +----------------+

	private static Worker startPruning() throws Exception {

		Worker worker = new Worker() {

			public void work() throws Exception {
				try {
					log.info(String.format("Starting prune thread; max age = %d days", cfg.PruneHistoryDays));
					
					while (!sleepyStop(cfg.PruneSleepSeconds)) {

						log.info(String.format("Pruning granular metrics > %d days old",
											   cfg.PruneHistoryDays));

						Instant epochMaxAge =
							Instant.now().minus(cfg.PruneHistoryDays, ChronoUnit.DAYS);
						
						store.pruneGranularMetrics(epochMaxAge);
					}
				}
				catch (InterruptedException e) {
					// time to go away, break out here
				}
				catch (Exception e) {
					log.severe("Exception in pruning thread; exiting: " + e.toString());
				}
			}

			public void cleanup(Exception e) {
				// nut-n-honey
			}
	
		};

		worker.start();
		
		return(worker);
	}

	private static void stopPruning(Worker pruner) throws Exception {
		pruner.signalStop();
		if (!pruner.waitForStop(cfg.PruneShutdownWaitSeconds)) {
			log.severe("Prune thread refused to stop");
		}
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static void msg(String fmt, Object ... args) {
		System.out.println(String.format(fmt, args));
	}

	private static Config cfg;
	private static WeatherStore store;
	
	private final static Logger log = Logger.getLogger(Archiver.class.getName());
}
