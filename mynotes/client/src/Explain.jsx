import { useEffect, useState } from 'react'
import MarkdownView from 'react-showdown';
import { Button, Dialog, DialogActions, DialogContent } from '@mui/material';
import { explain } from './lib/server.js';

import styles from './App.module.css'

export default function Explain({ inputText, onClose }) {

  const [result, setResult] = useState(undefined);
  const [error, setError] = useState(undefined);

  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	explain(inputText)
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
	
  }, [inputText]);

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
		  
		  <div style={{ paddingRight: '4px', borderRight: '1px solid grey', gridRow: 2, gridColumn: 1 }}
			   dangerouslySetInnerHTML={{ __html: inputText }}></div>
		  
		  <div style={{ gridRow: 2, gridColumn: 2 }}>
			
			{ error && renderMessage(error, true) }
			
			{ !error && !result && renderMessage("loading...", false) }
			
			{ !error && result && <MarkdownView
									className={styles.showdown}
									options={{ openLinksInNewWindow: true }}
									markdown={result} /> }
		  </div>
		</div>
	  </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
	  
	</Dialog>
  );
}


