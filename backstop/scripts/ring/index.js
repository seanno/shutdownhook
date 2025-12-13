//
//  ring/index.js
//
//  "RefreshToken" = ring refresh token
//  "LocationId" = location id to check
//  "BatteryWarningPct" = warn below this pct
//  "BatteryNotePct" = note below this pct
//

import util from 'util';
import { RingApi } from 'ring-client-api';


// +----------------+
// | checkBatteries |
// +----------------+

const DEFAULT_BATT_WARN_PCT = 20;
const DEFAULT_BATT_NOTE_PCT = 33;

async function checkBatteries(ringApi, location) {

  const battWarnPct = parseInt(process.env.BatteryWarningPct) || DEFAULT_BATT_WARN_PCT;
  const battNotePct = parseInt(process.env.BatteryNotePct) || DEFAULT_BATT_NOTE_PCT;
  
  var errs = [];
  var warns = [];
  var notes = [];
  
  // cameras
  if (location.cameras && location.cameras.length > 0) {
	for (var i = 0; i < location.cameras.length; ++i) {

	  const cam = location.cameras[i];
	  const name = cam.initialData.description;
	  const battPct = cam.initialData.battery_life;
	  const powerMode = cam.initialData.settings.power_mode;

	  if (powerMode !== "wired" && battPct !== null) {
		if (!battPct) errs.push(name);
		else if (battPct < battWarnPct) warns.push(name + ":" + battPct);
		else if (battPct < battNotePct) notes.push(name + ":" + battPct);
	  }
	}
  }

  // devices
  const devices = await location.getDevices();
  for (var i = 0; i < devices.length; ++i) {

	const dev = devices[i];
	const name = dev.initialData.name;
	const battPct = dev.initialData.batteryLevel;

	if (battPct !== undefined) {
	  if (battPct == 0) errs.push(name);
	  else if (battPct < battWarnPct) warns.push(name + ":" + battPct);
	  else if (battPct < battNotePct) notes.push(name + ":" + battPct);
	}
  }
  
  // log
  if (warns.length > 0) logWarning("Batteries", "LOW: " + warns.join(", "));
  if (errs.length > 0) logError("Batteries", "DEAD: " + errs.join(", "));
  
  if ((warns.length == 0 && errs.length == 0) || notes.length > 0) {
	logOK("Batteries", "Check: " + notes.join(", "));
  }
}

// +---------+
// | Helpers |
// +---------+

function logException(label, error) {
  console.log(`[STATUS]^ERROR^${label}^(${error.name}) ${zapNewlines(error.message)}`);
}

function logStatus(level, metric, result) {
  console.log(`[STATUS]^${level}^${metric}^${result}`);
}

function logError(metric, result) { logStatus("ERROR", metric, result); }
function logWarning(metric, result) { logStatus("WARNING", metric, result); }
function logOK(metric, result) { logStatus("OK", metric, result); }

function zapNewlines(input) {
  return(input.replace(/\s+/g, ' '));
}

// +------------+
// | Entrypoint |
// +------------+

async function ringBackstop() {

  const targetLocations = process.env.Locations.split(",");
  
  const ringApi = new RingApi({
	refreshToken: process.env.RefreshToken,
	locationIds: [ process.env.LocationId ]
  });

  const locations = await ringApi.getLocations();
  for (var i = 0; i < locations.length; ++i) {

	const location = locations[0];
	const name = location.locationDetails.name;

	if (location.disconnected) {
	  logError("Connection", "Location disconnected");
	}
	else {
	  try { await checkBatteries(ringApi, location); }
	  catch (error) { logException("checkBatteries", error); }
	}
  }

}

try {
  await ringBackstop();
  process.exit(0);
}
catch (error) {
  console.log(error);
  process.exit(1);
}


