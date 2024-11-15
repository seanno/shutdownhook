
const https = require('https');
const CsvReadableStream = require('csv-reader');
const AutoDetectDecoderStream = require('autodetect-decoder-stream');

const EPIC_TYPE = 'epic';
const ATHENA_TYPE = 'athena';
const CERNER_TYPE = 'cerner';
const SMART_TYPE = 'smart';

// +------+
// | epic |
// +------+

const EPIC_URL = 'https://open.epic.com/Endpoints/R4';

async function addEpicEndpoints(endpoints) {
  
  const response = await fetch(EPIC_URL);
  const bundle = await response.json();
  
  for (var i = 0; i < bundle.entry.length; ++i) {
	const name = bundle.entry[i].resource.name;
	const iss = bundle.entry[i].resource.address;
	if (name && iss) {
	  endpoints.push({
		type: EPIC_TYPE,
		clientId: 'd93b3b15-4eb4-4c80-9bdc-e75fc2311caa',
		label: name,
		iss: iss
	  });
	}
  }
}

// +--------+
// | athena |
// +--------+

const ATHENA_URL = 'https://fhir.athena.io/athena-fhir-urls/athenanet-fhir-base-urls.csv';

async function addAthenaEndpoints(endpoints) {
  
  return new Promise((resolve) => {
	
	const options = {
	  trim: true,
	  skipEmptyLines: true,
	  asObject: true
	};

	function addOne(endpoints, row) {
	  
	  if (!row.NAME || !row.URL ||
		  (row.NAME.toLowerCase().indexOf("do not use") !== -1)) {
		return;
	  }

	  endpoints.push({
		type: ATHENA_TYPE,
		clientId: '0oau9pjo41pYNRsC6297',
		label: row.NAME,
		iss: row.URL
	  });
	}

	https.get(ATHENA_URL, (res) => {
	  res.pipe(new AutoDetectDecoderStream())
		.pipe(new CsvReadableStream(options))
		.on('data', (row) => addOne(endpoints, row))
		.on('end', () => resolve(true));
	});
	
  });
}

// +------+
// | test |
// +------+

const testEndpoints = [
  {
	type: EPIC_TYPE,
	clientId: '2ec6d1a2-604d-4769-af2e-ba42f4011d02',
	label: 'Epic Sandbox (TEST)',
	iss: 'https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4/'
  },
  {
	type: CERNER_TYPE,
	clientId: 'faa3ae50-ca8d-482d-bf20-a2d41e4f2748',
	label: 'Cerner Sandbox (TEST)',
	iss: 'https://fhir-myrecord.cerner.com/r4/ec2458f2-1e24-41c8-b71b-0e701af7583d'
  },
  {
	type: ATHENA_TYPE,
	clientId: '0oau9i3bn42UFSyxo297',
	label: 'Athena Sandbox (TEST)',
	iss: 'https://api.preview.platform.athenahealth.com/fhir/r4/'
  },
  {
	type: SMART_TYPE,
	clientId: '43294215-4555-4ce2-811f-15cfd0fc3ae0',
	label: 'Smart Launcher (TEST)',
	iss: 'https://launch.smarthealthit.org/v/r4/sim/WzMsIiIsIiIsIkFVVE8iLDAsMCwwLCIiLCIiLCIiLCIiLCIiLCIiLCIiLDAsMSwiIl0/fhir'
  }
];

async function addTestEndpoints(endpoints) {

  endpoints.push(...testEndpoints);
}

// +-----------------+
// | sort and unique |
// +-----------------+

function sortAndUnique(endpoints) {
  
  endpoints.sort((a,b) => a.label.localeCompare(b.label));

  const unique = [];
  var lastLabel = undefined;
  
  for (var i = 0; i < endpoints.length; ++i) {

	const thisLabel = endpoints[i].label.toLowerCase();
	if (lastLabel && lastLabel === thisLabel) continue;

	unique.push(endpoints[i]);
	lastLabel = thisLabel;
  }

  return(unique);
}

// +------------+
// | entrypoint |
// +------------+

async function updateAll() {

  var endpoints = [];
  await addEpicEndpoints(endpoints);
  // await addAthenaEndpoints(endpoints);
  await addTestEndpoints(endpoints);

  endpoints = sortAndUnique(endpoints);
  
  console.log(JSON.stringify(endpoints, null, 2));
}

updateAll();

