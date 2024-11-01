 
// +----------+
// | fetchAll |
// +----------+

export async function fetchAll(fhir, resourceType, filterParams) {

  const entries = [];

  var url = resourceType + "?_count=100";
  if (filterParams) url += "&" + filterParams;

  while (url && entries.length <= 500) {
	
	const searchSet = await fhir.patient.request(url);
	//console.log(`fetchAllNative for ${resourceType}:\n${JSON.stringify(searchSet, null, 2)}`);
	
	if (!searchSet) return(undefined);
	if (!searchSet.entry || !searchSet.entry.length) return(entries);

	for (var i = 0; i < searchSet.entry.length; ++i) {
	  const resource = searchSet.entry[i].resource;
	  if (resource.resourceType !== resourceType) continue;
	  entries.push(resource);
	}

	url = getNextLink(searchSet);
  }
  
  return(entries);
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

export function filterByEncounter(fhir, encounterId) {
  return("encounter=" + encodeURIComponent((isEpic(fhir) ? 'Encounter/' : '') + encounterId));
}

function isEpic(fhir) {
  return(fhir.state.serverUrl.indexOf("epic.com") !== -1);
}



