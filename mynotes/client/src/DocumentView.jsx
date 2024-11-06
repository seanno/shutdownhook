import { useEffect, useState, useRef } from 'react'
import { fetchAndConvertToHtml } from './lib/convertToHtml.js';

import styles from './App.module.css'

export default function DocumentView({ fhir, doc }) {

  const [html, setHtml] = useState(undefined);
  const [error, setError] = useState(undefined);

  // +------------------+
  // | explain handlers |
  // +------------------+

  const explainRef = useRef(null);

  function explainClick() {
	const selectionText = window.getSelection().toString().trim();
	alert(selectionText);
  }
  
  function selectionChanged() {
	const selectionText = window.getSelection().toString().trim();
	explainRef.current.disabled = (selectionText === '');
  }
  
  useEffect(() => {

	if (!html) return;

	document.addEventListener('selectionchange', selectionChanged);

	return(() => {
	  document.removeEventListener('selectionchange', selectionChanged);
	});
	
  }, [html]);

  // +-------------+
  // | html effect |
  // +-------------+

  useEffect(() => {

	fetchAndConvertToHtml(fhir, doc)
	  .then((converted) => {
		// yay
		setHtml(converted);
		setError(undefined);
	  })
	  .catch((err) => {
		// boo
		console.log(JSON.stringify(err, null, 2));
		setHtml(undefined);
		setError('Unable to render document');
	  });
	
	return(() => {
	  setHtml(undefined);
	  setError(undefined);
	});
	
  }, [doc]);

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

  // if (html) console.log(html);
  
  return(
	<>
	  { error  && renderMessage(error, true) }
	  { !error && !html && renderMessage('loading...') }
	  
	  { !error && html && 
		<div style={{
			   display: 'grid',
			   gap: '8px',
			   height: '100%',
			   gridTemplateRows: '40px 1fr'
			 }}>

		  <div style={{ gridRow: 1 }}>
			<button
			  className={styles.explainButton}
			  ref={explainRef}
			  onClick={explainClick}
			  disabled>
			  Explain Selection
			</button>
		  </div>

		  <div style={{ gridRow: 2, paddingTop: '20px', overflowY: 'auto' }}
			   dangerouslySetInnerHTML={{ __html: html }}></div>
		  
		</div>
	  }
	</>
  );
}

