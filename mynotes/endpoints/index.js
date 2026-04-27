
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

const EPIC_URL = 'https://open.epic.com/Endpoints/Brands';
//const EPIC_URL = 'https://open.epic.com/Endpoints/R4';

async function addEpicEndpoints(endpoints) {
  
  const response = await fetch(EPIC_URL);
  const bundle = await response.json();
  //var fs = require('fs');
  //var bundle = JSON.parse(fs.readFileSync('/tmp/user-access-brands-endpoint-bundle.json', 'utf8'));
  
  const brands = {};
  const facilities = {};
  const epicEndpoints = {};

  // first collect everything by id
  for (var i = 0; i < bundle.entry.length; ++i) {

	const r = bundle.entry[i].resource;
	const id = bundle.entry[i].fullUrl;

	if (r.resourceType === 'Organization' && r.active) {
	  const o = { name: r.name };
	  if (r.partOf) {
		o.brandId = r.partOf.reference;
		facilities[id] = o;
	  }
	  else if (r.endpoint && r.endpoint.length) {
		o.endpointId = r.endpoint[0].reference;
		brands[id] = o;
	  }
	}
	else if (r.resourceType === 'Endpoint' && r.status === 'active') {
	  epicEndpoints[id] = { name: r.name, iss: r.address };
	}
  }

  // connect each brand up to its endpoint and output
  Object.keys(brands).forEach((brandId) => {

	const b = brands[brandId];
	const e = epicEndpoints[b.endpointId];
	if (!e || !e.iss) return;
	b.iss = e.iss;

	if (!b.name) b.name = e.name;
	if (!b.name || b.name.toLowerCase().startsWith('(inactive')) return;

	endpoints.push({
	  type: EPIC_TYPE,
	  clientId: 'd93b3b15-4eb4-4c80-9bdc-e75fc2311caa',
	  label: b.name,
	  iss: b.iss
	});
  });

  // output each facility
  Object.keys(facilities).forEach((facilityId) => {

	const f = facilities[facilityId];
	const b = brands[f.brandId];
	if (!b || !b.iss) return;

	if (!f.name) f.name = b.name;
	if (!f.name || f.name.toLowerCase().startsWith('(inactive')) return;
	
	endpoints.push({
	  type: EPIC_TYPE,
	  clientId: 'd93b3b15-4eb4-4c80-9bdc-e75fc2311caa',
	  label: f.name,
	  iss: b.iss
	});
  });
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

