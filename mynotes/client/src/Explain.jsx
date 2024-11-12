import { useEffect, useRef, useState } from 'react'
import MarkdownView from 'react-showdown';
import { Button, Dialog, DialogActions, DialogContent, TextField } from '@mui/material';
import { explain } from './lib/server.js';

import styles from './App.module.css'

export default function Explain({ initialText, onClose }) {

  const [explainText, setExplainText] = useState(initialText);
  const [editText, setEditText] = useState(undefined);
  
  const [result, setResult] = useState(undefined);
  const [error, setError] = useState(undefined);

  // +---------+
  // | actions |
  // +---------+

  function showEdit() {
	return(explainText && (result || error));
  }
  
  function onEdit() {
	setEditText(explainText);
	setExplainText(undefined);
	setResult(undefined);
	setError(undefined);
  }

  function showExplain() {
	return(!explainText && editText);
  }

  function onExplain() {
	setExplainText(editText);
	setEditText(undefined);
	setResult(undefined);
	setError(undefined);
  }

  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	if (!explainText) return;
	
	explain(explainText)
	  .then((result) => {
		setResult(result);
		setError(undefined);
	  })
	  .catch((err) => {
		console.log(JSON.stringify(err, null, 2));
		setError('error fetching explanation');
		setResult(undefined);
	  });

	return(() => {
	  setError(undefined);
	  setResult(undefined);
	});
	
  }, [explainText]);

  // +-------------+
  // | Main Render |
  // +-------------+

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
	<Dialog
	  onClose={onClose}
	  maxWidth='lg'
	  disableRestoreFocus
	  open={true} >
	  
	  <DialogContent>
		<div style={{
			   display: 'grid',
			   gridTemplateColumns: '50% 50%',
			   gap: '20px',
			 }} >

		  <div className={styles.expHeader} style={{ gridRow: 1, gridColumn: 1 }}>
			Original
		  </div>
		  
		  <div className={styles.expHeader} style={{ gridRow: 1, gridColumn: 2 }}>
			Explained by AI
		  </div>

		  { /* input text */ }

		  { explainText &&
			<div className={styles.expText} style={{ gridRow: 2, gridColumn: 1 }}
				 dangerouslySetInnerHTML={{ __html: explainText }}></div>
		  }

		  { !explainText &&
			<div className={styles.expText} style={{ gridRow: 2, gridColumn: 1 }}>
			  <TextField
				multiline
				autoFocus
				label='input text'
				variant='outlined'
				value={editText}
				onChange={(evt) => setEditText(evt.target.value)}
				onKeyDown={(evt) => { if (evt.key === 'Enter' && editText) { onExplain() } } }
			  />
			</div>
		  }
		  
		  { /* results */ }

		  <div style={{ gridRow: 2, gridColumn: 2 }}>
			
			{ error && renderMessage(error, true) }
			
			{ !error && !result && explainText && renderMessage("loading...", false) }
			
			{ !error && result && <MarkdownView
									className={styles.showdown}
									options={{ openLinksInNewWindow: true }}
									markdown={result} /> }
		  </div>
		</div>
	  </DialogContent>

      <DialogActions>
        { showEdit() && <Button onClick={onEdit}>Edit</Button> }
		{ showExplain() && <Button onClick={onExplain}>Explain</Button> }
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
	  
	</Dialog>
  );
}


