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

public class App 
{
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("java ... [path to config] capture");
			System.err.println("java ... [path to config] server");
			System.err.println("java ... [path to config] cam [save path]");
			System.err.println("java ... [path to config] noaa");
			System.err.println("java ... [path to config] predictImage [save path] [ISO DateTime]");
			return;
		}

		Easy.setSimpleLogFormat("INFO");

		String action = args[1].toLowerCase();
		String json = Easy.stringFromSmartyPath(args[0]);
		Tides.Config cfg = Tides.Config.fromJson(json);

		Tides tides = null;
		Camera cam = null;
		NOAA noaa = null;

		try {

			switch (action) {
				case "capture":
					log.info("Capturing current tide information");
					tides = new Tides(cfg);
					if (tides.captureCurrentTide() == null) log.severe("Failed to capture tide");
					break;
				
				case "server":
					tides = new Tides(cfg);
					// nyi
					break;

				case "cam":
					cam = new Camera(cfg.Camera);
					cam.takeSnapshot(args[2]);
					break;

				case "noaa":
					noaa = new NOAA(cfg.NOAA);
					NOAA.Predictions predictions = noaa.getPredictions();
					System.out.println("===== PREDICTIONS:");
					System.out.println(predictions.toString());
					Instant now = Instant.now();
					System.out.println("===== CURRENT ESTIMATE:");
					System.out.println(String.format("\t%s\n",predictions.estimateTide(now)));
					System.out.println("===== NEXT EXTREMES:\n");
					System.out.println(predictions.nextExtremes());
					break;

				case "predictimage":
					tides = new Tides(cfg);
					Instant when = (args.length >= 4 ? Instant.parse(args[3]) : Instant.now());
					File img = tides.imageForInstant(when);
					Files.copy(img.toPath(), Paths.get(args[2]));
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
