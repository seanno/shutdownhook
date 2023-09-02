/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tides;

import java.io.File;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import com.shutdownhook.toolbox.Easy;

import com.google.gson.Gson;

public class App 
{
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("java ... [path to config] capture");
			System.err.println("java ... [path to config] server");
			System.err.println("java ... [path to config] cam [save path]");
			System.err.println("java ... [path to config] noaa [isodatetime]");
			System.err.println("java ... [path to config] predict [isodatetime]");
			return;
		}

		Easy.setSimpleLogFormat("FINE");

		String action = args[1].toLowerCase();
		String json = Easy.stringFromSmartyPath(args[0]);
		Server.Config cfg = Server.Config.fromJson(json);

		Tides tides = null;
		Camera cam = null;
		NOAA noaa = null;
		Instant when;

		try {

			switch (action) {
				case "capture":
					log.info("Capturing current tide information");
					tides = new Tides(cfg.Tides);
					if (tides.captureCurrentTide() == null) log.severe("Failed to capture tide");
					break;
				
				case "server":
					Server server = new Server(cfg);
					server.runSync();
					break;

				case "cam":
					cam = new Camera(cfg.Tides.Camera);
					cam.takeSnapshot(args[2]);
					break;

				case "noaa":
					noaa = new NOAA(cfg.Tides.NOAA);
					when = (args.length >= 3 ? Instant.parse(args[2]) : Instant.now());
					NOAA.Predictions predictions = noaa.getPredictions(when);
					System.out.println("===== PREDICTIONS:");
					System.out.println(predictions.toString());
					System.out.println("===== CURRENT ESTIMATE:");
					System.out.println(String.format("\t%s\n",predictions.estimateTide(when)));
					System.out.println("===== NEXT EXTREMES:\n");
					System.out.println(predictions.nextExtremes(5));
					break;

				case "predict":
					tides = new Tides(cfg.Tides);
					when = (args.length >= 3 ? Instant.parse(args[2]) : Instant.now());
					Tides.TideForecast forecast = tides.forecastTide(when);
					System.out.println(new Gson().toJson(forecast));
					break;

				default:
					System.err.println("ACTION unknown");
					return;
			}
		}
		finally {
			if (tides != null) tides.close();
			if (cam != null) cam.close();
			if (noaa != null) noaa.close();
		}
	}

	private final static Logger log = Logger.getLogger(App.class.getName());
}
