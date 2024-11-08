
// bundle downloaded from https://open.epic.com/Endpoints/R4
import epic_endpoints from './epic-endpoints.json';

// +-------+
// | types |
// +-------+

const EPIC_TYPE = 'epic';
const CERNER_TYPE = 'cerner';
const SMART_TYPE = 'smart';

// +------------------------+
// | getEndpoints           |
// | addEndpoints_HardCoded |
// | addEndpoints_Epic      |
// +------------------------+

export function getEndpoints() {

  const endpoints = [];
  addEndpoints_Epic(endpoints);
  addEndpoints_HardCoded(endpoints);

  return(endpoints);
}

function addEndpoints_HardCoded(endpoints) {

  for (var i = 0; i < hardCodedEndpoints.length; ++i) {
	endpoints.push(hardCodedEndpoints[i]);
  }
}

function addEndpoints_Epic(endpoints) {

  const epic = {
	type: EPIC_TYPE,
	name: 'Epic Production R4',
	clientId: 'd93b3b15-4eb4-4c80-9bdc-e75fc2311caa',
	endpoints: []
  };
  
  for (var i = 0; i < epic_endpoints.entry.length; ++i) {
	const name = epic_endpoints.entry[i].resource.name;
	const iss = epic_endpoints.entry[i].resource.address;
	if (name && iss) epic.endpoints.push({ name: name, iss: iss });
  }

  endpoints.push(epic);
}

// +---------+
// | ehrType |
// | isEpic  |
// +---------+

window.cachedEhrTypes = {};

export function ehrType(fhir) {

  const iss = fhir.state.serverUrl;
  if (window.cachedEhrTypes[iss]) return(window.cachedEhrTypes[iss]);

  const endpoints = getEndpoints();
  for (var i = 0; i < endpoints.length; ++i) {
	for (var j = 0; j < endpoints[i].endpoints.length; ++j) {
	  if (endpoints[i].endpoints[j].iss === iss) {
		window.cachedEhrTypes[iss] = endpoints[i].type;
		return(endpoints[i].type);
	  }
	}
  }

  returN(undefined);
}

export function isEpic(fhir) {
  return(ehrType(fhir) === EPIC_TYPE);
}

// +----------------------+
// | hard-coded endpoints |
// +----------------------+

const hardCodedEndpoints = [
  {
	type: EPIC_TYPE,
	name: 'Epic Sandbox',
	clientId: '2ec6d1a2-604d-4769-af2e-ba42f4011d02',
	endpoints: [
	  {
		name: 'Epic Sandbox (TEST)',
		iss: 'https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4/'
	  }
	]
  },
  {
	name: 'Cerner Sandbox',
	clientId: 'faa3ae50-ca8d-482d-bf20-a2d41e4f2748',
	endpoints: [
	  {
		name: 'Cerner Sandbox (TEST)',
		iss: 'https://fhir-myrecord.cerner.com/r4/ec2458f2-1e24-41c8-b71b-0e701af7583d'
	  }
	]
  },
  {
	type: SMART_TYPE,
	name: 'Smart Launcher',
	clientId: '43294215-4555-4ce2-811f-15cfd0fc3ae0',
	endpoints: [
	  {
		name: 'Smart Launcher (TEST)',
		iss: 'https://launch.smarthealthit.org/v/r4/sim/WzMsIiIsIiIsIkFVVE8iLDAsMCwwLCIiLCIiLCIiLCIiLCIiLCIiLCIiLDAsMSwiIl0/fhir'
	  }
	]
  }
];

