/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Note we're using Pi4J v1.3 which is that last version that still supports JDK8.

package com.shutdownhook.zwave;

import java.io.Closeable;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import com.shutdownhook.toolbox.Easy;

public class Motion implements Closeable, GpioPinListenerDigital
{
	// +------------------+
	// | Config and Setup |
	// +------------------+

	public static class Config
	{
		public List<Sensor> Sensors;
	}

	public static class Sensor
	{
		public String Name;
		public Integer WiringPiPinNumber;
		public List<Action> Actions;
	}
	
	public static class Action
	{
		public String Name;

		public String StartTimeHHMM;
		public String EndTimeHHMM;
		public Boolean OnlyIfOff = false;
		
		public String ScreenId;
		public String ActionSettingId;
		public String QuietSettingId;
	}
	
	public interface Handler {
		public void runSetting(String screenId, String settingId) throws Exception;
		public int getSettingLevel(String screenId, String settingId) throws Exception;
	}
	
	public Motion(final Config cfg, final Handler handler) throws Exception {
		this.cfg = cfg;
		this.handler = handler;

		if (cfg.Sensors == null || cfg.Sensors.size() == 0) {
			log.info("No motion sensors configured.");
			this.gpio = null;
			this.quietActions = null;
		}
		else {
		
			this.gpio = GpioFactory.getInstance();
			this.quietActions = new ArrayList<QuietAction>();
		
			startListening();
		}
	}
	
	public void close() {
		if (gpio != null) gpio.shutdown();
	}

	// +----------+
	// | Listener |
	// +----------+

	private void startListening() throws Exception {

		for (Sensor sensor : cfg.Sensors) {

			log.info(String.format("Adding sensor at pin %d (%s)",
								   sensor.WiringPiPinNumber, sensor.Name));
			
			GpioPinDigitalInput pin = gpio.provisionDigitalInputPin(
											RaspiPin.getPinByAddress(sensor.WiringPiPinNumber),
											PinPullResistance.PULL_DOWN);

			pin.setName(sensor.Name);
			pin.setShutdownOptions(true);
			pin.addListener(this);
		}
	}

	public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {

		int pinNumber = event.getPin().getPin().getAddress();

		switch (event.getEdge()) {
			case FALLING:
				doQuietActions(pinNumber);
				break;

			case RISING:
				doMotionActions(pinNumber);
				break;

		    default:
				log.warning("Why am I getting this edge state in listener: " +
							event.getEdge().toString());
				break;
		}

	}

	private void doQuietActions(int pinNumber) {
		for (QuietAction qa : popQuietActions(pinNumber)) {
			executeAction(qa.ActionName, qa.ScreenId, qa.SettingId, pinNumber, "quiet");
		}
	}

	private void doMotionActions(int pinNumber) {
			
		for (Sensor sensor : cfg.Sensors) {
			if (sensor.WiringPiPinNumber == pinNumber) {

				log.fine(String.format("MOTION: motion for pin %d (%s)",
									   sensor.WiringPiPinNumber, sensor.Name));

				for (Action action : sensor.Actions) {
					
					if (actionActive(action)) {

						executeAction(action.Name, action.ScreenId, action.ActionSettingId,
									  pinNumber, "motion");

						if (action.QuietSettingId != null) {
							pushQuietAction(action.Name, pinNumber, action.ScreenId, action.QuietSettingId);
						}
					}
					else {
						log.fine(String.format("MOTION: Skipping inactive action %s on pin %d",
											   action.Name, pinNumber));
					}
				}
				
			}
		}
	}

	private boolean actionActive(Action action) {

		LocalTime timeNow = LocalTime.now();
		int nowMinute = (timeNow.getHour() * 60) + timeNow.getMinute();
		
		// before start time?
		int startMinute = 0;
		if (action.StartTimeHHMM != null) {
			String[] split = action.StartTimeHHMM.split(":");
			startMinute = (Integer.parseInt(split[0]) * 60) + Integer.parseInt(split[1]);
		}

		if (nowMinute < startMinute) return(false);
 
		// after end time?
		int endMinute = 1440;
		if (action.EndTimeHHMM != null) {
			String[] split = action.EndTimeHHMM.split(":");
			endMinute = (Integer.parseInt(split[0]) * 60) + Integer.parseInt(split[1]);
		}

		if (nowMinute > endMinute) return(false);

		// only if all lights in setting are off?

		if (!action.OnlyIfOff) return(true);

		int maxLevel = 0;
		try {
			maxLevel = handler.getSettingLevel(action.ScreenId, action.ActionSettingId);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "getSettingLevel", true));
		}

		return(maxLevel == 0);
	}
	
	private void executeAction(String actionName, String screenId, String settingId,
							   int pinNumber, String trigger) {
		
		log.info(String.format("MOTION: executing %s action %s %s/%s on pin %d",
							   trigger, actionName, screenId, settingId, pinNumber));
		
		try {
			handler.runSetting(screenId, settingId);
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "executeAction: " + trigger, true));
		}
	}
	
	private synchronized void pushQuietAction(String actionName, int pinNumber,
											  String screenId, String settingId) {
		QuietAction qa = new QuietAction();
		qa.ActionName = actionName;
		qa.PinNumber = pinNumber;
		qa.ScreenId = screenId;
		qa.SettingId = settingId;
		
		quietActions.add(qa);
	}
	
	private synchronized List<QuietAction> popQuietActions(int pinNumber) {
		
		List<QuietAction> qas = new ArrayList<QuietAction>();

		int i = 0;
		while (i < quietActions.size()) {
			if (quietActions.get(i).PinNumber == pinNumber) {
				qas.add(quietActions.get(i));
				quietActions.remove(i);
			}
			else {
				++i;
			}
		}

		return(qas);
	}

	// +-------------------+
	// | Helpers & Members |
	// +-------------------+

	public static class QuietAction
	{
		public String ActionName;
		public int PinNumber;
		public String ScreenId;
		public String SettingId;
	}
	
	final private Config cfg;
	final private Handler handler;
	final private GpioController gpio;
	final private List<QuietAction> quietActions;

	private final static Logger log = Logger.getLogger(Motion.class.getName());
}
