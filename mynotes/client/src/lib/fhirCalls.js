
import { b64_to_str } from './b64.js';

// +----------+
// | fetchAll |
// +----------+

export async function fetchAll(fhir, resourceType, encounterId) {

  const entries = [];

  var url = resourceType + "?_count=100";
  if (encounterId) url += "&" + filterByEncounter(fhir, encounterId);

  while (url && entries.length <= 500) {
	
	const searchSet = await fhir.patient.request(url);
	//console.log(`fetchAllNative for ${resourceType}:\n${JSON.stringify(searchSet, null, 2)}`);
	
	if (!searchSet) return(undefined);
	if (!searchSet.entry || !searchSet.entry.length) return(entries);

	for (var i = 0; i < searchSet.entry.length; ++i) {
	  const resource = searchSet.entry[i].resource;
	  if (resource.resourceType !== resourceType) continue;
	  if (!checkEncounterId(resource, encounterId)) continue;
	  entries.push(resource);
	}

	url = getNextLink(searchSet);
  }
  
  return(entries);
}

function checkEncounterId(r, id) {

  // is this real life?
  if (!r.context) return(true);
  if (!r.context.encounter || r.context.encounter.length === 0) return(true);

  for (var i = 0; i < r.context.encounter.length; ++i) {
	const e = r.context.encounter[i];
	if (e.reference && e.reference.indexOf(id) !== -1) return(true);
  }

  return(false);
}

function getNextLink(searchSet) {
  
  if (!searchSet.link) return(undefined);
  
  for (var i = 0; i < searchSet.link.length; ++i) {
	if (searchSet.link[i].relation === 'next') {
	  return(searchSet.link[i].url);
	}
  }
  
  return(undefined);
}

// +-------------+
// | fetchBase64 |
// +-------------+

export async function fetchBase64(fhir, fhirAttachment) {

  let base64 = fhirAttachment.data;

  if (!base64) {

	if (!fhirAttachment.url || fhirAttachment.url.indexOf('Binary/') === -1) {
	  
	  console.warn(JSON.stringify(fhirAttachment, null, 2));
	  throw(new Error("Attachment needs data or Binary resource url"));
	}

	const fhirBinary = await fhir.request({
	  url: fhirAttachment.url,
	  headers: { Accept: 'application/fhir+json' }
	});

	base64 = fhirBinary.data;
  }

  return(base64);
}

// +-------------------+
// | filterByEncounter |
// +-------------------+

function filterByEncounter(fhir, encounterId) {
  return("encounter=" + encodeURIComponent((isEpic(fhir) ? 'Encounter/' : '') + encounterId));
}

// +--------+
// | isEpic |
// +--------+

function isEpic(fhir) {

  try {
	const tokenParts = fhir.state.tokenResponse.access_token.split(".");
	const tokenJson = b64_to_str(tokenParts[1]);
	return(tokenJson.indexOf("\"epic.") !== -1);
  }
  catch (err) {
	// token not a JWT
	return(false);
  }
  
}




