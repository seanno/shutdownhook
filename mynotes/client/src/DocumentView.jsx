import { useEffect, useState } from 'react'
import { Button } from '@mui/material';
import { fetchBase64 } from './lib/fhirCalls.js';
import { b64_to_str } from './lib/b64.js';
import CdaStyler from './CdaStyler.jsx';

import styles from './App.module.css'

export default function DocumentView({ fhir, doc }) {

  const [data, setData] = useState(undefined);
  const [contentType, setContentType] = useState(undefined);
  const [error, setError] = useState(undefined);

  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	const att = findAttachment();

	if (att == null) {
	  setError('No renderable content found.');
	}
	else if (att.data) {
	  // data is local
	  setData(att.data);
	  setContentType(att.contentType);
	}
	else {

	  fetchBase64(fhir, att)
		.then(result => {
		  setData(result);
		  setContentType(att.contentType);
		})
		.catch(error => {
		  console.error(erro);
		  setError('Error loading binary data');
		});
	}
	
	return(() => {
	  setData(undefined);
	  setContentType(undefined);
	  setError(undefined);
	});
	
  }, [doc]);

  // +---------+
  // | Helpers |
  // +---------+
  
  function findAttachment() {
	
	for (var i = 0; i < doc.content.length; ++i) {
	  
	  const att = doc.content[i].attachment;
	  if (!att.contentType) continue;

	  switch (att.contentType) {

		// easy ones
		case 'text/html': return(att);
		case 'application/pdf': return(att);

		//  CDA
		case 'application/xml':
		  if (doc.content[i].format.code.startsWith('urn:hl7-org:sdwg:ccda-structuredBody:')) {
			return(att);
		  }
	  }
	}

	return(null);
  }

  // +------------+
  // | renderData |
  // +------------+

  function renderPDF() {
	return(
	  <iframe
		src={'data:application/pdf;base64,' + encodeURIComponent(data)}
		width="100%"
		height="98%"
		style={{ border: 'none', margin: '0px', padding: '0px' }} >
	  </iframe>
	);
  }

  function renderHTML() {
	const html = b64_to_str(data);
	return(<div dangerouslySetInnerHTML={{ __html: html }}></div>);
  }

  function renderData() {
	switch (contentType) {
	  case 'text/html': return(renderHTML());
	  case 'application/pdf': return(renderPDF());
	  case 'application/xml': return(<CdaStyler xmlText={b64_to_str(data)} />);
	}

	// should never get here
	console.error('why am i here?');
	return(<></>);
  }

  // +---------------+
  // | renderMessage |
  // +---------------+

  function renderMessage(msg, isError) {
	return(
	  <div className={ isError ? styles.msg : styles.err }>{msg}</div>
	);
  }

  // +-------------+
  // | Main Render |
  // +-------------+

  return(
	<>
	  { error  && renderMessage(error, true) }
	  { !error && !data && renderMessage('loading...') }
	  { !error && data && renderData() }
	</>
  );
}

