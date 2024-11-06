
import { b64_to_str } from './b64.js';
import { fetchBase64 } from './fhirCalls.js';
import { convertPDF } from './server.js';

// +-----------------------+
// | fetchAndConvertToHtml |
// +-----------------------+

export async function fetchAndConvertToHtml(fhir, docRef) {
  
  const att = findBestAttachment(docRef);
  if (att == null) {
	console.log(JSON.stringify(docRef, null, 2));
	throw new Error('Document format unrecognized');
  }

  const base64data = await fetchBase64(fhir, att);
  return(await convertToHtml(base64data, att.contentType));
}

// +--------------------+
// | findBestAttachment |
// +--------------------+

function findBestAttachment(docRef) {

  var bestAttachment = undefined;
  var bestScore = -1;
  
  for (var i = 0; i < docRef.content.length; ++i) {

	const thisScore = scoreAttachment(docRef.content[i]);

	if (thisScore > bestScore) {
	  
	  bestScore = thisScore;
	  bestAttachment = docRef.content[i].attachment;
	}
  }
  
  return(bestAttachment);
}

// -1 means can't handle it at all; otherwise > is better
// text = 10, pdf = 25, ccda = 50, html = 100

function scoreAttachment(content) {
  
  switch (content.attachment.contentType) {
	  
	case 'text/html':
	  // best
	  return(100);

	case 'text/plain':
	  // best
	  return(10);

	case 'application/pdf':
	  // ok but html translation may be so-so
	  return(25);
	  
	case 'application/xml':
	  // ccda, pretty good
	  return(content && content.format && content.format.code &&
			 content.format.code.startsWith('urn:hl7-org:sdwg:ccda-structuredBody:')
			 ? 50 : -1);
  }

  return(-1);
}

// +---------------+
// | convertToHtml |
// +---------------+

async function convertToHtml(base64data, contentType) {

  switch (contentType) {
	case 'text/html': return(b64_to_str(base64data));
	case 'text/plain': return("<pre><code>" + b64_to_str(base64data) + "</code></pre>");
	case 'application/xml': return(await styleCCDA(base64data));
	case 'application/pdf': return(await convertPDF(base64data));
  }

  return(undefined);
}

// +-----------+
// | styleCCDA |
// +-----------+

async function styleCCDA(base64data) {

  const xmlText = b64_to_str(base64data);
  
  const response = await fetch('CDA.xsl');
  const xslText = await response.text();

  const parser = new DOMParser();
  const xslDoc = parser.parseFromString(xslText, "application/xml");
  const xmlDoc = parser.parseFromString(xmlText, "application/xml");

  const xslt = new XSLTProcessor();
  xslt.importStylesheet(xslDoc);
  const styledDoc = xslt.transformToDocument(xmlDoc);

  const serializer = new XMLSerializer();
  var styledHtml = serializer.serializeToString(styledDoc);

  return(styledHtml);
}

