/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.tesla;

import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.shutdownhook.toolbox.Easy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class App 
{
	public static enum Action
	{
		VEHICLES,
		VEHICLE,
		HONK,
		NEARBY,
		MAP,
		MILEAGE,
		INSIDETEMP,
		OUTSIDETEMP
	}

	private static void usage() {
		System.err.println("List vehicles:\tjava -cp PATH_TO_JAR [settings] vehicles");
		System.err.println("Vehicle details:\tjava -cp PATH_TO_JAR [settings] vehicle ID");
		System.err.println("Map URL:\tjava -cp PATH_TO_JAR [settings] map ID");
		System.err.println("Mileage:\tjava -cp PATH_TO_JAR [settings] mileage ID");
		System.err.println("Inside Temp (F):\tjava -cp PATH_TO_JAR [settings] insideTemp ID");
		System.err.println("Outisde Temp(F):\tjava -cp PATH_TO_JAR [settings] outsideTemp ID");
		System.err.println("");
		System.err.println("Options (also can be set in env):");
		System.err.println("  * -Dtview_email=EMAIL");
		System.err.println("  * -Dtview_password=PASSWORD");
		System.err.println("  * -Dtview_loglevel=FINE/INFO/WARNING/SEVERE (Default WARNING)");
	}

	public static class ConsoleCaptchaSolver implements Tesla.CaptchaSolver {
		public String solve(String imagePath) throws Exception {
			System.out.println("CAPTCHA solve required; image at: " + imagePath);
			System.out.print("> ");
			return(new Scanner(System.in).nextLine().trim());
		}
	}
	
	
    public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			usage();
			return;
		}

		Easy.setSimpleLogFormat();
		
		Level level = Level.parse(Easy.superGetProperty("tview_loglevel", "WARNING"));
		Logger.getLogger("").setLevel(level);

		action = Action.valueOf(args[0].toUpperCase());

		Tesla.Config cfg = new Tesla.Config();
		cfg.Email = Easy.superGetProperty("tview_email", null);
		cfg.Password = Easy.superGetProperty("tview_password", null);
		cfg.ClientId = Easy.superGetProperty("tview_clientid", null);
		cfg.ClientSecret = Easy.superGetProperty("tview_clientsecret", null);

		if (cfg.Email == null || cfg.Password == null) {
			throw new Exception("tview_email and tview_password must be set in env or props");
		}

		try {
			gson = new GsonBuilder().setPrettyPrinting().create(); 
			tesla = new Tesla(cfg, new ConsoleCaptchaSolver());
			String vehicleId = (args.length >= 2 ? args[1] : null);

			switch (action) {
			    case VEHICLES:
					System.out.println(gson.toJson(tesla.getVehicles()));
					break;

			    case VEHICLE:
					System.out.println(gson.toJson(tesla.getVehicleData(vehicleId)));
				    break;

			    case HONK:
					System.out.println(Boolean.toString(tesla.honk(vehicleId)));
				    break;

			    case NEARBY:
					System.out.println(gson.toJson(tesla.getNearbyChargers(vehicleId)));
				    break;

			    case MAP:
					System.out.println(tesla.getMapUrl(vehicleId));
					break;
					
			    case MILEAGE:
					System.out.println(Double.toString(tesla.getMileage(vehicleId)));
					break;

			    case INSIDETEMP:
					System.out.println(Double.toString(tesla.getInsideTemp(vehicleId)));
					break;
					
			    case OUTSIDETEMP:
					System.out.println(Double.toString(tesla.getOutsideTemp(vehicleId)));
					break;
			}
		
		}
		finally {
			if (tesla != null) tesla.close();
		}
				
    }

	private static Tesla tesla;
	private static Gson gson;
	private static Action action;
}
