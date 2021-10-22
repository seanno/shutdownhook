/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.zwave;

import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import com.shutdownhook.toolbox.Easy;

public class App 
{
    public static void main(String[] args) throws Exception {
		
		Easy.setSimpleLogFormat();

		String path = Easy.superGetProperty("config", "~/.zway");
		String json = Easy.stringFromSmartyPath(path);
		ZWay.Config cfg = ZWay.Config.fromJson(json);

		try {
			gson = new GsonBuilder().setPrettyPrinting().create();
			zway = new ZWay(cfg);

			if (args.length == 0 || args[0].equalsIgnoreCase("devices")) {
				listDevices();
			}
			else {
				String deviceId = args[0];
				String arg = (args.length == 1 ? "" : args[1].toLowerCase());

				if (args.length == 1 || arg.equals("metrics")) {
					JsonObject metrics = zway.getMetrics(deviceId, true);
					System.out.println(gson.toJson(metrics));
				}
				else if (arg.equals("level")) {
					System.out.println(Integer.toString(zway.getLevel(deviceId, true)));
				}
				else if (arg.equals("exact")) {
					zway.setLevel(deviceId, Integer.parseInt(args[2]));
				}
				else if (arg.equals("on")) {
					zway.turnOn(deviceId);
				}
				else if (arg.equals("off")) {
					zway.turnOff(deviceId);
				}
				else {
					usage();
				}
			}
		}
		finally {
			if (zway != null) zway.close();
		}
    }

	private static void usage() {
		System.out.println("List all devices: devices");
		System.out.println("Device metrics: [deviceId] metrics");
	}

	private static void listDevices() throws Exception {
		
		Map<String,ZWay.Device> devices = zway.getDevices();

		for (ZWay.Device device : devices.values()) {
			System.out.println(device.getName() + "\t" +
							   device.getType() + "\t" +
							   device.getId());
		}
	}

	private static ZWay zway;
	private static Gson gson;
		
	private final static Logger log = Logger.getLogger(App.class.getName());
}
