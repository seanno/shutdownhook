
// +-------+
// | types |
// +-------+

const EPIC_TYPE = 'epic';
const CERNER_TYPE = 'cerner';
const SMART_TYPE = 'smart';

// +-----------+
// | endpoints |
// +-----------+

export const endpoints = [
  {
	type: EPIC_TYPE,
	name: 'Epic Production',
	clientId: '9b078bf5-343d-4679-a18e-12c1d13613d8',
	endpoints: [
	  {
		name: 'Overlake Hospital Medical Center',
		iss: 'https://sfd.overlakehospital.org/FHIRproxy/api/FHIR/R4/'
	  },
	  {
		name: 'Mass General Brigham',
		iss: 'https://ws-interconnect-fhir.partners.org/Interconnect-FHIR-MU-PRD/api/FHIR/R4/'
	  }
	]
  },
  {
	type: EPIC_TYPE,
	name: 'Epic Sandbox',
	clientId: 'd8029749-b555-4c16-ad29-bd636c5404c9',
	endpoints: [
	  {
		name: 'Epic Sandbox',
		iss: 'https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4/'
	  }
	]
  },
  {
	type: SMART_TYPE,
	name: 'Smart Launcher',
	clientId: '43294215-4555-4ce2-811f-15cfd0fc3ae0',
	endpoints: [
	  {
		name: 'Smart Launcher',
		iss: 'https://launch.smarthealthit.org/v/r4/sim/WzMsIiIsIiIsIkFVVE8iLDAsMCwwLCIiLCIiLCIiLCIiLCIiLCIiLCIiLDAsMSwiIl0/fhir'
	  }
	]
  }
];

// +---------+
// | ehrType |
// | isEpic  |
// +---------+

window.cachedEhrTypes = {};

export function ehrType(fhir) {

  const iss = fhir.state.serverUrl;
  if (window.cachedEhrTypes[iss]) return(window.cachedEhrTypes[iss]);

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
