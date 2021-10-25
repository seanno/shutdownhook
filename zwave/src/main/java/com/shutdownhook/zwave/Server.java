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
		public Queue.Config Queue = new Queue.Config();

		public List<Screen> Screens;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public static class Screen
	{
		public String Id;
		public String Name;
		public List<Setting> Settings;
		public List<VLight> VLights;
	}

	public static class Setting
	{
		public String Id;
		public String Name;
		public List<SettingValue> Values;
	}

	public static class SettingValue
	{
		public String VLightId;
		public Integer Level;
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
		queue = null;

		try {
			startQueueListener();
			
			server = WebServer.create(cfg.Server);

			registerScreenHandler();
			registerSettingHandler();
			registerExactHandler();
			registerOnOffHandler();
			registerHomeHandler();
			
			server.runSync();
		}
		finally {
			if (queue != null) queue.close();
			zway.close();
		}
    }
	
	// +----------------+
	// | Queue Listener |
	// +----------------+

	private static void startQueueListener() throws Exception {
		
		queue = new Queue(cfg.Queue, new Queue.Handler() {

			public void handle(String screenName, String screenSetting) throws Exception {

				// NOTE THIS IS DIFFERENT THAN SETTINGHANDLER ... that method
				// takes Id values embedded into html; this one searches by id or name.
				// We are sloppier about param validation; deal with it... the exception
				// handler will manage the queue properly.

				Screen screen = null;
				for (Screen s : cfg.Screens) {
					if (screenName.equalsIgnoreCase(s.Id) ||
						screenName.equalsIgnoreCase(s.Name)) {
						
						screen = s;
						break;
					}
				}

				if (screenSetting.equalsIgnoreCase("off")) {
					turnOffScreen(screen);
				}
				else if (screenSetting.equalsIgnoreCase("on")) {
					turnOnScreen(screen);
				}
				else {
					Setting setting = null;
					for (Setting s : screen.Settings) {
						if (screenSetting.equalsIgnoreCase(s.Id) ||
							screenSetting.equalsIgnoreCase(s.Name)) {
							
							setting = s;
							break;
						}
					}

					for (SettingValue sv : setting.Values) {
						VLight vlight = screen.VLights.get(findVLight(screen, sv.VLightId));
						setVLightLevel(vlight, sv.Level);
					}
				}
				
			}
		});
	}
						  
	// +---------------+
	// | OnOff Handler |
	// +---------------+

	private static void registerOnOffHandler() throws Exception {

		server.registerHandler("/onoff", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				Screen screen = cfg.Screens.get(findScreen(screenId));

				Boolean on = request.QueryParams.get("cmd").equalsIgnoreCase("on");
				if (on) turnOnScreen(screen);
				else turnOffScreen(screen);

				response.Status = 200;
			}

		});
	}

	// +-----------------+
	// | Setting Handler |
	// +-----------------+

	private static void registerSettingHandler() throws Exception {

		server.registerHandler("/setting", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				Screen screen = cfg.Screens.get(findScreen(screenId));

				String settingId = request.QueryParams.get("setting");
				Setting setting = screen.Settings.get(findSetting(screen, settingId));

				for (SettingValue sv : setting.Values) {
					VLight vlight = screen.VLights.get(findVLight(screen, sv.VLightId));
					setVLightLevel(vlight, sv.Level);
				}

				response.Status = 200;
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
				Screen screen = cfg.Screens.get(findScreen(screenId));
												
				String vlightId = request.QueryParams.get("vlight");
				VLight vlight = screen.VLights.get(findVLight(screen, vlightId));
				
				int val = Integer.parseInt(request.QueryParams.get("val"));
				setVLightLevel(vlight, val);

				response.Status = 200;
			}

		});
	}

	// +----------------+
	// | Screen Handler |
	// +----------------+

	private final static String TKN_SCREEN_NAME = "SCREEN_NAME";
	private final static String TKN_SCREEN_ID = "SCREEN_ID";
	private final static String TKN_PREV_SCREEN_ID = "PREV_SCREEN_ID";
	private final static String TKN_NEXT_SCREEN_ID = "NEXT_SCREEN_ID";
	private final static String TKN_SETTING_NAME = "SETTING_NAME";
	private final static String TKN_SETTING_ID = "SETTING_ID";
	private final static String TKN_VLIGHT_NAME = "VLIGHT_NAME";
	private final static String TKN_VLIGHT_ID = "VLIGHT_ID";
	private final static String TKN_VLIGHT_BRIGHTNESS = "VLIGHT_BRIGHTNESS";
	
	private final static String RPT_VLIGHTS = "VLIGHTS";
	private final static String RPT_SETTINGS = "SETTINGS";
	private final static String RPT_SCREENS = "SCREENS";
	
	private static void registerScreenHandler() throws Exception {

		String templateText = Easy.stringFromResource("screen.html.tmpl");
		final Template template = new Template(templateText);

		server.registerHandler("/screen", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				int iscreen = findScreen(screenId);
				final Screen screen = cfg.Screens.get(iscreen);

				final Map<String,String> tokens = new HashMap<String,String>();
				tokens.put(TKN_SCREEN_NAME, screen.Name);
				tokens.put(TKN_SCREEN_ID, screen.Id);

				int iprev = iscreen - 1; if (iprev < 0) iprev = cfg.Screens.size() - 1;
				tokens.put(TKN_PREV_SCREEN_ID, cfg.Screens.get(iprev).Id);

				int inext = iscreen + 1; if (inext >= cfg.Screens.size()) inext = 0;
				tokens.put(TKN_NEXT_SCREEN_ID, cfg.Screens.get(inext).Id);
				
				response.setHtml(template.render(tokens, new Template.TemplateProcessor() {

					public boolean repeat(String[] args, int counter) {
						try {
							if (args[0].equals(RPT_VLIGHTS)) {
								return(setVLightTokens(counter));
							}
							else {
								return(setSettingTokens(counter));
							}
						}
						catch (Exception e) {
							log.severe(Easy.exMsg(e, "screenhandler", false));
							return(false);
						}
					}

					private boolean setSettingTokens(int i) throws Exception {
						
						if (screen.Settings == null) return(false);
						if (i >= screen.Settings.size()) return(false);

						Setting setting = screen.Settings.get(i);
						tokens.put(TKN_SETTING_NAME, setting.Name.toLowerCase());
						tokens.put(TKN_SETTING_ID, setting.Id);

						return(true);
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

	// +--------------+
	// | Home Handler |
	// +--------------+

	private static void registerHomeHandler() throws Exception {

		String templateText = Easy.stringFromResource("home.html.tmpl");
		final Template template = new Template(templateText);

		server.registerHandler("/", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				Map<String,String> tokens = new HashMap<String,String>();
				
				response.setHtml(template.render(tokens, new Template.TemplateProcessor() {

					public boolean repeat(String[] args, int counter) {
						if (counter == cfg.Screens.size()) return(false);
						tokens.put(TKN_SCREEN_NAME, cfg.Screens.get(counter).Name);
						tokens.put(TKN_SCREEN_ID, cfg.Screens.get(counter).Id);
						return(true);
					}

				}));
			}
		});
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static void turnOnScreen(Screen screen) throws Exception {
		for (VLight vl : screen.VLights) turnOnVLight(vl);
	}

	private static void turnOffScreen(Screen screen) throws Exception {
		for (VLight vl : screen.VLights) turnOffVLight(vl);
	}
	
	private static void turnOnVLight(VLight vlight) throws Exception {
		for (String deviceId : vlight.Devices) zway.turnOn(deviceId);
	}
									 
	private static void turnOffVLight(VLight vlight) throws Exception {
		for (String deviceId : vlight.Devices) zway.turnOff(deviceId);
	}

	private static void setVLightLevel(VLight vlight, int level) throws Exception {
		for (String deviceId : vlight.Devices) zway.setLevel(deviceId, level);
	}

	private static int findSetting(Screen screen, String settingId) throws Exception {

		if (settingId == null || settingId.isEmpty()) {
			return(0);
		}

		for (int i = 0; i < screen.Settings.size(); ++i) {
			if (settingId.equals(screen.Settings.get(i).Id)) {
				return(i);
			}
		}

		throw new Exception("Setting not found in " + screen.Id + ": " + settingId);
	}

	private static int findVLight(Screen screen, String vlightId) throws Exception {

		if (vlightId == null || vlightId.isEmpty()) {
			return(0);
		}

		for (int i = 0; i < screen.VLights.size(); ++i) {
			if (vlightId.equals(screen.VLights.get(i).Id)) {
				return(i);
			}
		}

		throw new Exception("VLight not found in " + screen.Id + ": " + vlightId);
	}
	
	private static int findScreen(String screenId) throws Exception {
		
		if (screenId == null || screenId.isEmpty()) {
			return(0);
		}

		for (int i = 0; i < cfg.Screens.size(); ++i) {
			if (screenId.equals(cfg.Screens.get(i).Id)) {
				return(i);
			}
		}

		throw new Exception("Screen not found: " + screenId);
	}
	
	private static Config cfg;
	private static WebServer server;
	private static ZWay zway;
	private static Queue queue;
	
	private final static Logger log = Logger.getLogger(Server.class.getName());
}
