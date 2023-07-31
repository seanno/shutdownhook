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
		public Lights.Config Lights = new Lights.Config();
		public Motion.Config Motion = new Motion.Config();
		public WebServer.Config Server = new WebServer.Config();
		public ZWay.Config ZWay = new ZWay.Config();
		public Queue.Config Queue = new Queue.Config();

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
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
		lights = new Lights(cfg.Lights, zway);
		queue = null;
		motion = null;

		try {
			startQueueListener();
			startMotionListener();
			
			server = WebServer.create(cfg.Server);

			registerScreenHandler();
			registerSettingHandler();
			registerExactHandler();
			registerOnOffHandler();
			registerHomeHandler();
			
			server.runSync();
		}
		finally {
			if (motion != null) motion.close();
			if (queue != null) queue.close();
			zway.close();
		}
    }
	
	// +------------------+
	// | Motion Listenter |
	// +------------------+

	private static void startMotionListener() throws Exception {

		motion = new Motion(cfg.Motion, new Motion.Handler() {
				
			public void runSetting(String screenId, String settingId) throws Exception {
				lights.settingSet(screenId, settingId);
			}
		
			public int getSettingLevel(String screenId, String settingId) throws Exception {
				return(lights.settingGet(screenId, settingId));
			}

		});
	}

	// +----------------+
	// | Queue Listener |
	// +----------------+

	private static void startQueueListener() throws Exception {
		
		queue = new Queue(cfg.Queue, new Queue.Handler() {

			public void handle(String screenName, String screenSetting) throws Exception {
				lights.settingSet(screenName, screenSetting);
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
				Boolean on = request.QueryParams.get("cmd").equalsIgnoreCase("on");

				if (on) lights.screenOn(screenId);
				else lights.screenOff(screenId);

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
				String settingId = request.QueryParams.get("setting");

				lights.settingSet(screenId, settingId);
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
				String vlightId = request.QueryParams.get("vlight");
				int val = Integer.parseInt(request.QueryParams.get("val"));

				lights.vlightSet(screenId, vlightId, val);
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
	private final static String TKN_VLIGHT_MAX = "VLIGHT_MAX";
	
	private final static String RPT_VLIGHTS = "VLIGHTS";
	private final static String RPT_SETTINGS = "SETTINGS";
	private final static String RPT_SCREENS = "SCREENS";
	
	private static void registerScreenHandler() throws Exception {

		String templateText = Easy.stringFromResource("screen.html.tmpl");
		final Template template = new Template(templateText);

		server.registerHandler("/screen", new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				String screenId = request.QueryParams.get("screen");
				final Lights.Screen screen = lights.findScreen(screenId);

				final Map<String,String> tokens = new HashMap<String,String>();
				tokens.put(TKN_SCREEN_NAME, screen.Name);
				tokens.put(TKN_SCREEN_ID, screen.Id);

				List<Lights.Screen> screens = lights.getScreens();
				
				int iprev = screen.Index - 1; if (iprev < 0) iprev = screens.size() - 1;
				tokens.put(TKN_PREV_SCREEN_ID, screens.get(iprev).Id);

				int inext = screen.Index + 1; if (inext >= screens.size()) inext = 0;
				tokens.put(TKN_NEXT_SCREEN_ID, screens.get(inext).Id);
				
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

						Lights.Setting setting = screen.Settings.get(i);
						tokens.put(TKN_SETTING_NAME, setting.Name.toLowerCase());
						tokens.put(TKN_SETTING_ID, setting.Id);

						return(true);
					}

					private boolean setVLightTokens(int i) throws Exception {
						
						if (i >= screen.VLights.size()) {
							return(false);
						}

						Lights.VLight vlight = screen.VLights.get(i);
						tokens.put(TKN_VLIGHT_NAME, vlight.Name);
						tokens.put(TKN_VLIGHT_ID, vlight.Id);

						int max = vlight.getMaxLevel(zway);
						int level = vlight.getLevel(zway);
						if (level > max) level = max;
						
						tokens.put(TKN_VLIGHT_BRIGHTNESS, Integer.toString(level));
						tokens.put(TKN_VLIGHT_MAX, Integer.toString(max));
						
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
						List<Lights.Screen> screens = lights.getScreens();
						if (counter == screens.size()) return(false);
						tokens.put(TKN_SCREEN_NAME, screens.get(counter).Name);
						tokens.put(TKN_SCREEN_ID, screens.get(counter).Id);
						return(true);
					}

				}));
			}
		});
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private static Config cfg;
	private static ZWay zway;
	private static Lights lights;
	private static Motion motion;
	private static WebServer server;
	private static Queue queue;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
