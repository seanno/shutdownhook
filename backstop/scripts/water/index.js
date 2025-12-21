//
//  water/index.js
//

const WATER_URL = "https://iofuncs-20250620153219202.azurewebsites.net/api/witter-data?tz=GMT&days=1";
const MIN_TANK_CM = 95;
const MAX_AGE_MINS = 45;
	  
// +------------+
// | checkWater |
// +------------+

async function checkWater() {

  const response = await fetch(WATER_URL);
  const data = await response.json();

  if (!data || data.length < 1) throw new Error("Failed fetching data");

  const errs = [];
  
  const actualCM = Number(data[0].Value);
  if (actualCM < MIN_TANK_CM) errs.push(`Tank level low: ${actualCM} cm (min ${MIN_TANK_CM} cm)`);
	
  const actualDT = new Date(data[0].When);
  const actualAgeMinutes = (new Date() - actualDT) / (1000 * 60);
  if (actualAgeMinutes > MAX_AGE_MINS) {
	errs.push(`Tank level not reported for ${actualAgeMinutes.toFixed(0)} minutes`);
  }

  if (errs.length > 0) {
	console.log(`[STATUS]^ERROR^${errs.join("; ")}`);
  }
  else {
	console.log(`[STATUS]^OK^Last Reading ${actualAgeMinutes.toFixed(0)} minutes ago; Level ${actualCM} cm`);
  }
}

// +------------+
// | Entrypoint |
// +------------+

try {
  await checkWater();
  process.exit(0);
}
catch (error) {
  console.log(error);
  process.exit(1);
}


