/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.zwave;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

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
		public ZWay.Config ZWay = new ZWay.Config();

		public List<Screen> Screens;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public static class Screen
	{
		public String Id;
		public String Name;
		public List<VLight> VLights;
	}

	public static class VLight
	{
		public String Id;
		public String Name;
		public List<String> Devices;
	}

	// +------------+
	// | Entrypoint |
	// +------------+

    public static void main(String[] args) throws Exception {

		Easy.setSimpleLogFormat();
		
		if (args.length < 1) {
			System.out.println("Usage: java -cp [path_to_jar] \\\n" +
							   "\tcom.shutdownhook.zwave.Server \\\n" +
							   "\t[path_to_config] \n");
			return;
		}

		cfg = Config.fromJson(Easy.stringFromSmartyPath(args[0]));
		zway = new ZWay(cfg.ZWay);

		try {
			server = WebServer.create(cfg.Server);

			registerScreenHandler();
			registerExactHandler();
			registerOnOffHandler();
			
			server.runSync();
		}
		finally {
			zway.close();
		}
    }
	
	// +---------------+
	// | OnOff Handler |
	// +---------------+

	private static void registerOnOffHandler() throws Exception {

		server.registerHandler("/onoff", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				Boolean on = request.QueryParams.get("cmd").equalsIgnoreCase("on");

				boolean handled = false;
				for (Screen screen : cfg.Screens) {
					if (screenId.equals(screen.Id)) {
						for (VLight vlight : screen.VLights) {
							for (String deviceId : vlight.Devices) {
								if (on) zway.turnOn(deviceId);
								else zway.turnOff(deviceId);
							}
						}
						handled = true;
					}
				}

				response.Status = (handled ? 200 : 500);
			}

		});
	}

	// +---------------+
	// | Exact Handler |
	// +---------------+

	private static void registerExactHandler() throws Exception {

		server.registerHandler("/exact", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				String vlightId = request.QueryParams.get("vlight");
				int val = Integer.parseInt(request.QueryParams.get("val"));

				boolean handled = false;
				for (Screen screen : cfg.Screens) {
					if (screenId.equals(screen.Id)) {
						for (VLight vlight : screen.VLights) {
							if (vlightId.equals(vlight.Id)) {

								for (String deviceId : vlight.Devices) {
									zway.setLevel(deviceId, val);
								}
								
								handled = true;
							}
						}
					}
				}

				response.Status = (handled ? 200 : 500);
			}

		});
	}

	// +----------------+
	// | Screen Handler |
	// +----------------+

	private final static String TKN_SCREEN_NAME = "SCREEN_NAME";
	private final static String TKN_SCREEN_ID = "SCREEN_ID";
	private final static String TKN_VLIGHT_NAME = "VLIGHT_NAME";
	private final static String TKN_VLIGHT_ID = "VLIGHT_ID";
	private final static String TKN_VLIGHT_BRIGHTNESS = "VLIGHT_BRIGHTNESS";
	
	private final static String RPT_VLIGHTS = "VLIGHTS";
	
	private static void registerScreenHandler() throws Exception {

		String templateText = Easy.stringFromResource("screen.html.tmpl");
		final Template template = new Template(templateText);

		server.registerHandler("/screen", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenName = request.QueryParams.get("screen");
				final Screen screen = findScreen(screenName);

				final Map<String,String> tokens = new HashMap<String,String>();
				tokens.put(TKN_SCREEN_NAME, screen.Name);
				tokens.put(TKN_SCREEN_ID, screen.Id);

				response.setHtml(template.render(tokens, new Template.TemplateProcessor() {

					public boolean repeat(String[] args, int counter) {
						try {
							return(setVLightTokens(counter));
						}
						catch (Exception e) {
							log.severe(Easy.exMsg(e, "screenhandler", false));
							return(false);
						}
					}

					private boolean setVLightTokens(int i) throws Exception {
						
						if (i >= screen.VLights.size()) {
							return(false);
						}

						VLight vlight = screen.VLights.get(i);
						tokens.put(TKN_VLIGHT_NAME, vlight.Name);
						tokens.put(TKN_VLIGHT_ID, vlight.Id);

						int maxLevel = 0;
						for (String deviceId : vlight.Devices) {
							int level = zway.getLevel(deviceId, false);
							if (level > maxLevel) maxLevel = level;
						}

						tokens.put(TKN_VLIGHT_BRIGHTNESS, Integer.toString(maxLevel));
						return(true);
					}

				}));
			}
		});
	}

	private static Screen findScreen(String screenName) throws Exception {
		
		if (screenName == null || screenName.isEmpty()) {
			return(cfg.Screens.get(0));
		}

		for (Screen screen : cfg.Screens) {
			if (screenName.equals(screen.Name)) {
				return(screen);
			}
		}

		throw new Exception("Screen not found: " + screenName);
	}
	
	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static Config cfg;
	private static WebServer server;
	private static ZWay zway;
	
	private final static Logger log = Logger.getLogger(Server.class.getName());
}
