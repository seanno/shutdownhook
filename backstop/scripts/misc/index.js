//
//  misc/index.js
//

import util from 'util';

// +---------+
// | helpers |
// +---------+

function stringify(obj) {
  return(util.inspect(data, {
	compact: false, // Forces multi-line output
	colors: true    // Adds ANSI color codes for better readability
  }));
}

// +-------------------+
// | checkBellevueZWay |
// +-------------------+

const BV_ZWAY_URL = "https://backstop2.blob.core.windows.net/backstop-transfer/zbattery.json?sp=r&st=2026-01-07T06:54:46Z&se=2050-01-07T15:09:46Z&spr=https&sv=2024-11-04&sr=b&sig=W4IKmIGcVKnY616k7PX8j%2FKX4u7gKMDxSkfhblULdnE%3D";

const BV_ZWAY_LINK = "https://find.z-wave.me/smarthome/#/zwave/batteries";

const BV_ZWAY_METRIC = "B Family Main Switch";

const BV_ZWAY_MAX_AGE_HOURS = 24;

const BV_ZWAY_WARN_BATTERY_PCT = 20;

async function checkBellevueZWay() {

  try {
	const response = await fetch(BV_ZWAY_URL);
	const data = await response.json();
	if (!data || data.length < 1) throw new Error("Failed fetch");

	
	const updated = new Date(data.updateTime * 1000);
	console.log(updated);
	const ageHours = Math.floor((new Date() - updated) / (1000 * 60 * 60));
	if (ageHours > BV_ZWAY_MAX_AGE_HOURS) {
	  console.log(`[STATUS]^ERROR^No update for ${ageHours} hours^${BV_ZWAY_METRIC}^${BV_ZWAY_LINK}`);
	  return;
	}
	
	const batteryPct = data.metrics.level;
	if (batteryPct < BV_ZWAY_WARN_BATTERY_PCT) {
	  const msg = (batteryPct == 0 ? "DEAD" : `${batteryPct}%`);
	  const level = (batteryPct == 0 ? "ERROR" : "WARNING");
	  console.log(`[STATUS]^${level}^Battery ${msg}^${BV_ZWAY_METRIC}^${BV_ZWAY_LINK}`);
	  return;
	}

	console.log(`[STATUS]^OK^Battery ${batteryPct}%^${BV_ZWAY_METRIC}^${BV_ZWAY_LINK}`);
  }
  catch (error) {
	console.log(`[STATUS]^ERROR^${stringify(error)}^${BV_ZWAY_METRIC}^${BV_ZWAY_LINK}`);
  }
}

// +------------+
// | Entrypoint |
// +------------+

try {
  await checkBellevueZWay();
}
finally {
  process.exit(0);
}


