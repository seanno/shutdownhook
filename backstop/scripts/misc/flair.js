//
// misc/flair.js
//
// process.env.FlairClientId
//            .FlairClientSecret

import { stringify } from './utility.js';

// +-----------+
// | Constants |
// +-----------+

const FLAIR_BASE_URL = "https://api.flair.co";

const FLAIR_TOKEN_URL = "/oauth2/token";
const FLAIR_VENTS_URL = "/api/vents";

const FLAIR_METRIC = "V Flair Vents";
const FLAIR_LINK = "https://my.flair.co";

const MIN_VOLTAGE = 2.5;

// +------------+
// | checkFlair |
// +------------+

export async function checkFlair() {
  
  try {
	const clientId = process.env.FlairClientId;
	const clientSecret = process.env.FlairClientSecret;

	if (!clientId || !clientSecret) {
	  console.log(`[STATUS]^ERROR^Missing Flair configuration^${FLAIR_METRIC}^${FLAIR_LINK}`);
	  return;
	}

	const tokenForm = new FormData();
	tokenForm.append("client_id", clientId);
	tokenForm.append("client_secret", clientSecret);
	tokenForm.append("grant_type", "client_credentials");
	tokenForm.append("scope", "vents.view");
	
	const tokenResponse = await fetch(FLAIR_BASE_URL + FLAIR_TOKEN_URL, { method: 'POST', body: tokenForm });
	const tokenData = await tokenResponse.json();

	if (!tokenData || !tokenData.access_token) {
	  console.log(`[STATUS]^ERROR^No token returned^${FLAIR_METRIC}^${FLAIR_LINK}`);
	  return;
	}

	const ventsResponse = await fetch(FLAIR_BASE_URL + FLAIR_VENTS_URL, {
	  method: 'GET',
	  headers: { 'Authorization': `Bearer ${tokenData.access_token}` }
	});

	const ventsData = await ventsResponse.json();
	if (!ventsData || !ventsData.data || ventsData.data.length === 0) {
	  console.log(`[STATUS]^ERROR^No vents returned^${FLAIR_METRIC}^${FLAIR_LINK}`);
	}

	const ok = [];
	
	for (const vent of ventsData.data) {
	  
	  const voltage = vent.attributes.voltage;
	  const name = vent.attributes.name;

	  if (vent.inactive || !voltage) {
		console.log(`[STATUS]^WARNING^${name} DEAD^${FLAIR_METRIC}^${FLAIR_LINK}`);
	  }
	  else if (voltage < MIN_VOLTAGE) {
		console.log(`[STATUS]^WARNING^${name} ${voltage}v^${FLAIR_METRIC}^${FLAIR_LINK}`);
	  }
	  else {
		ok.push(`${name} ${voltage}v`);
	  }
	}

	if (ok.length > 0) {
	  console.log(`[STATUS]^OK^${ok.join(", ")}^${FLAIR_METRIC}^${FLAIR_LINK}`);
	}
  }
  catch (error) {
	console.log(`[STATUS]^ERROR^${stringify(error)}^${FLAIR_METRIC}^${FLAIR_LINK}`);
  }
}
