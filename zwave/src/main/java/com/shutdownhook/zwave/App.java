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

			if (args.length == 0) {
				usage();
			}
			else if (args[0].equalsIgnoreCase("devices")) {
				listDevices();
			}
			else {
				String deviceId = args[0];
				String arg = (args.length == 1 ? "" : args[1].toLowerCase());

				if (args.length == 1 || arg.equals("level")) {
					int level = zway.findDevice(deviceId).getLevel(true);
					System.out.println(Integer.toString(level));
				}
				else if (arg.equals("exact")) {
					zway.findDevice(deviceId).setLevel(Integer.parseInt(args[2]));
				}
				else if (arg.equals("on")) {
					zway.findDevice(deviceId).turnOn();
				}
				else if (arg.equals("off")) {
					zway.findDevice(deviceId).turnOff();
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
		System.out.println("Device on: [deviceId] on");
		System.out.println("Device off: [deviceId] off");
		System.out.println("Device get level: [deviceId] level");
		System.out.println("Device set level: [deviceId] exact [0-100]");
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
