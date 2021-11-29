/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.zwave;

import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

public class Lights
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public final static String ON_SETTING = "on";
	public final static String OFF_SETTING = "off";
	
	public static class Config
	{
		public List<Screen> Screens;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}

		public void hydrate(Lights lights) {
			for (int i = 0; i < Screens.size(); ++i) {
				Screens.get(i).hydrate(lights, i);
			}
		}
	}

	public static class Screen
	{
		public String Id;
		public String Name;
		public List<Setting> Settings;
		public List<VLight> VLights;

		public void on(ZWay zway) throws Exception {
			for (VLight vlight : VLights) vlight.on(zway);
		}

		public void off(ZWay zway) throws Exception {
			for (VLight vlight : VLights) vlight.off(zway);
		}

		public void setLevel(ZWay zway, int level) throws Exception {
			for (VLight vlight : VLights) vlight.setLevel(zway, level);
		}

		public int getLevel(ZWay zway) throws Exception {
			return(getLevel(zway, false));
		}

		public int getLevel(ZWay zway, boolean refresh) throws Exception {

			int maxLevel = 0;
		
			for (VLight vlight : VLights) {
				int thisLevel = vlight.getLevel(zway, refresh);
				if (thisLevel > maxLevel) maxLevel = thisLevel;
			}

			return(maxLevel);
		}

		public transient int Index;
		
		public void hydrate(Lights lights, int index) {

			this.Index = index;
			
			if (Settings != null) {
				for (Setting setting : Settings) {
					for (SettingValue value : setting.Values) {
						value.hydrate(lights, this);
					}
				}
			}
		}
	}

	public static class Setting
	{
		public String Id;
		public String Name;
		public List<SettingValue> Values;

		public void set(ZWay zway) throws Exception {
			for (SettingValue value : Values) value.set(zway);
		}

		public int get(ZWay zway) throws Exception {
			return(get(zway, false));
		}

		public int get(ZWay zway, boolean refresh) throws Exception {
			
			int maxLevel = 0;

			for (SettingValue value : Values) {
				int thisLevel = value.get(zway, refresh);
				if (thisLevel > maxLevel) maxLevel = thisLevel;
			}

			return(maxLevel);
		}
	}

	public static class SettingValue
	{
		public String VLightId;
		public Integer Level;

		public void set(ZWay zway) throws Exception {
			vlight.setLevel(zway, Level);
		}
		
		public int get(ZWay zway) throws Exception {
			return(get(zway, false));
		}

		public int get(ZWay zway, boolean refresh) throws Exception {
			return(vlight.getLevel(zway, refresh));
		}

		private transient VLight vlight;
		public void hydrate(Lights lights, Screen screen) {
			vlight = lights.findVLight(screen, VLightId);
		}
	}
	
	public static class VLight
	{
		public String Id;
		public String Name;
		public List<String> Devices;

		public void on(ZWay zway) throws Exception {
			for (String deviceId : Devices) zway.findDevice(deviceId).turnOn();
		}

		public void off(ZWay zway) throws Exception {
			for (String deviceId : Devices) zway.findDevice(deviceId).turnOff();
		}

		public void setLevel(ZWay zway, int level) throws Exception {
			for (String deviceId : Devices) zway.findDevice(deviceId).setLevel(level);
		}

		public int getLevel(ZWay zway) throws Exception {
			return(getLevel(zway, false));
		}
		
		public int getLevel(ZWay zway, boolean refresh) throws Exception {

			int maxLevel = 0;
		
			for (String deviceId : Devices) {
				int thisLevel = zway.findDevice(deviceId).getLevel(refresh);
				if (thisLevel > maxLevel) maxLevel = thisLevel;
			}

			return(maxLevel);
		}

		public int getMaxLevel(ZWay zway) throws Exception {
			for (String deviceId : Devices) {
				if (!zway.findDevice(deviceId).isBinary()) return(100);
			}
			return(1);
		}
	}

	public Lights(Config cfg, ZWay zway) throws Exception {

		this.cfg = cfg;
		cfg.hydrate(this);

		this.zway = zway;
	}

	// +--------------------+
	// | Screen Convenience |
	// +--------------------+

	public void screenOn(String screenIdOrName) throws Exception {
		settingSet(screenIdOrName, ON_SETTING);
	}

	public void screenOff(String screenIdOrName) throws Exception {
		settingSet(screenIdOrName, OFF_SETTING);
	}

	// +---------------------+
	// | Setting Convenience |
	// +---------------------+

	public void settingSet(String screenIdOrName, String settingIdOrName) throws Exception {

		Screen screen = findScreen(screenIdOrName);

		if (settingIdOrName.equalsIgnoreCase(ON_SETTING)) {
			screen.on(zway);
		}
		else if (settingIdOrName.equalsIgnoreCase(OFF_SETTING)) {
			screen.off(zway);
		}
		else {
			findSetting(screen, settingIdOrName).set(zway);
		}
	}

	public int settingGet(String screenIdOrName, String settingIdOrName) throws Exception {
		return(settingGet(screenIdOrName, settingIdOrName, false));
	}
	
	public int settingGet(String screenIdOrName, String settingIdOrName,
						  boolean refresh) throws Exception {

		Screen screen = findScreen(screenIdOrName);

		if (settingIdOrName.equalsIgnoreCase(ON_SETTING) ||
			settingIdOrName.equalsIgnoreCase(OFF_SETTING)) {
			
			return(screen.getLevel(zway, refresh));
		}
		else {
			return(findSetting(screen, settingIdOrName).get(zway, refresh));
		}
	}

	// +--------------------+
	// | VLight Convenience |
	// +--------------------+

	public void vlightOn(String screenIdOrName, String idOrName)
		throws Exception {
		
		findVLight(screenIdOrName, idOrName).on(zway);
	}
	
	public void vlightOff(String screenIdOrName, String idOrName)
		throws Exception {
		
		findVLight(screenIdOrName, idOrName).off(zway);
	}

	public void vlightSet(String screenIdOrName, String vlightIdOrName, int level)
		throws Exception {
		
		findVLight(screenIdOrName, vlightIdOrName).setLevel(zway, level);
	}

	public int vlightGet(String screenIdOrName, String vlightIdOrName)
		throws Exception {

		return(vlightGet(screenIdOrName, vlightIdOrName, false));
	}
	
	public int vlightGet(String screenIdOrName, String vlightIdOrName, boolean refresh)
		throws Exception {
		
		return(findVLight(screenIdOrName, vlightIdOrName).getLevel(zway, refresh));
	}

	// +---------+
	// | Lookups |
	// +---------+

	public List<Screen> getScreens() { return(cfg.Screens); }
	
	public Screen findScreen(String idOrName) {

		Screen screenByName = null;
		
		for (Screen screen : cfg.Screens) {
			if (idOrName.equalsIgnoreCase(screen.Id)) return(screen);
			if (idOrName.equalsIgnoreCase(screen.Name)) screenByName = screen;
		}

		return(screenByName);
	}

	public VLight findVLight(String screenIdOrName, String vlightIdOrName) {
		return(findVLight(findScreen(screenIdOrName), vlightIdOrName));
	}
	
	public VLight findVLight(Screen screen, String idOrName) {

		VLight vlightByName = null;
		
		for (VLight vlight : screen.VLights) {
			if (idOrName.equalsIgnoreCase(vlight.Id)) return(vlight);
			if (idOrName.equalsIgnoreCase(vlight.Name)) vlightByName = vlight;
		}

		return(vlightByName);
	}

	public Setting findSetting(String screenIdOrName, String settingIdOrName) {
		return(findSetting(findScreen(screenIdOrName), settingIdOrName));
	}

	public Setting findSetting(Screen screen, String idOrName) {

		Setting settingByName = null;
		
		for (Setting setting : screen.Settings) {
			if (idOrName.equalsIgnoreCase(setting.Id)) return(setting);
			if (idOrName.equalsIgnoreCase(setting.Name)) settingByName = setting;
		}

		return(settingByName);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	private Config cfg;
	private ZWay zway;
	
	private final static Logger log = Logger.getLogger(Lights.class.getName());
}
