import { useEffect, useState } from 'react'
import { fetchAndConvertToHtml } from './lib/convertToHtml.js';
import { Button } from '@mui/material';
import Explain from './Explain.jsx';

import styles from './App.module.css'

export default function DocumentView({ fhir, doc }) {

  const [html, setHtml] = useState(undefined);
  const [error, setError] = useState(undefined);

  const [selectedText, setSelectedText] = useState(undefined);
  const [showExplain, setShowExplain] = useState(false);
  
  // +------------------+
  // | explain handlers |
  // +------------------+

  function explainClick(evt) {
	setShowExplain(true);
  }

  function onExplainClose() {
	setShowExplain(false);
  }
  
  function selectionChanged() {
	setSelectedText(window.getSelection().toString().trim());
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
			<Button
			  className={styles.explainButton}
			  onClick={(evt) => explainClick(evt)}
			  disabled={!selectedText}
			  >
			  Explain Selection
			</Button>
		  </div>

		  <div style={{ gridRow: 2, paddingTop: '20px', overflowY: 'auto' }}
			   dangerouslySetInnerHTML={{ __html: html }}></div>

		  { showExplain && <Explain initialText={selectedText} onClose={onExplainClose} /> }
		  
		</div>
	  }
	</>
  );
}

