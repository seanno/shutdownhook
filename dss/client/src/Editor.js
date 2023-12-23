
import { useState } from 'react';
import { Alert, Button, Checkbox, FormControlLabel, Snackbar, TextField } from '@mui/material';
import { serverDeleteQuery, serverSaveQuery } from './lib/server.js';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';

import styles from './Editor.module.css';

export default function Editor({ query, closeFn, runFn, schemaFn }) {

  const [statement, setStatement] = useState(query.Statement);
  const [shared, setShared] = useState(query.IsShared);
  const [description, setDescription] = useState(query.Description);
  const [paramsCsv, setParamsCsv] = useState(query.ParamsCsv);

  const [newQuery, setNewQuery] = useState(!query.Id);
  const [dirty, setDirty] = useState(false);
  const [everSaved, setEverSaved] = useState(false);
  
  const [showToastSaved,setShowToastSaved] = useState(false);
  const [showToastErrorSave,setShowToastErrorSave] = useState(false);
  const [showToastErrorDelete,setShowToastErrorDelete] = useState(false);
  
  // +--------+
  // | Clicks |
  // +--------+

  async function saveClick() {

	query.Statement = statement;
	query.Description = description;
	query.ParamsCsv = paramsCsv;
	query.IsShared = shared;

	try {
	  const ret = await serverSaveQuery(query);
	  query.Id = ret.id;
	  setDirty(false);
	  setEverSaved(true);
	  setNewQuery(false);
	  setShowToastSaved(true);
	}
	catch (err) {
	  console.error(err);
	  setShowToastErrorSave(true);
	}
  }

  function runClick() {

	const q = {
	  ConnectionName: query.ConnectionName,
	  Description: description,
	  ParamsCsv: paramsCsv
	}

	if (query.Id && !dirty) {
	  q.Id = query.Id;
	}
	else {
	  q.Statement = statement;
	}

	runFn(q);
  }

  function backClick() {
	const msg = 'Are you sure? Changes to his query will not be saved.';
	if (!dirty || window.confirm(msg)) closeFn(everSaved);
  }

  async function deleteClick() {
	
	if (!window.confirm("Are you sure? This can't be undone!")) return;

	try {
	  const resp = await serverDeleteQuery(query.Id);
	  if (!resp.success) throw new Error("Failed deleting query");
	  closeFn(true);
	}
	catch (err) {
	  console.error(err);
	  setShowToastErrorDelete(true);
	}
  }
  
  // +-------------+
  // | Main Render |
  // +-------------+

  return (

	<div className={styles.container}>
	  
	  <div className={styles.metadata}>
		
		<span title="display name for query" className={styles.label}>Description:</span>
		<TextField
		  value={ description || '' }
		  variant='outlined'
		  size='small'
		  onChange={ (evt) => { setDescription(evt.target.value); setDirty(true) } }
		  inputProps={{ style: { minWidth: '265px', paddingTop: '1px', paddingBottom: '1px' } }}
		/>

		<FormControlLabel
          label='Shared'
          control={<Checkbox
					 size='small'
					 checked={ shared }
					 onChange={ () => { setShared(!shared); setDirty(true); } }
					 style={{ padding: '0px', marginLeft: '32px', marginRight: '4px' }}
                   />}
		/>
	  </div>

	  <div className={styles.metadata}>
		
		<span title="CSV in name:default format, default optional" className={styles.label}>Parameters:</span>
		<TextField
		  value={ paramsCsv || '' }
		  variant='outlined'
		  size='small'
		  onChange={ (evt) => { setParamsCsv(evt.target.value); setDirty(true) } }
		  inputProps={{ style: { minWidth: '400px', paddingTop: '1px', paddingBottom: '1px' } }}
		/>

	  </div>

	  <div className={styles.codemirror}>
		
		<CodeMirror
		  autoFocus
		  extensions={[ sql({}) ]}
		  basicSetup={{ autocompletion: false }}
		  value={ statement }
		  onChange={ (value, viewUpdate) => { setStatement(value); setDirty(true); } }
		/>
		
	 </div>

	  <div className={styles.buttons}>
		
		<Button variant="outlined" onClick={runClick}>Run</Button>
		<Button variant="outlined" disabled={!dirty} onClick={saveClick}>Save</Button>
		<Button variant="outlined" disabled={newQuery} onClick={deleteClick}>Delete</Button>
		<Button variant="outlined" onClick={schemaFn}>Schema</Button>
		<Button variant="outlined" onClick={backClick}>Back to List</Button>

		<Snackbar open={showToastSaved} onClose={() => setShowToastSaved(false)} autoHideDuration={3000}>
		  <Alert severity="success">Query Saved</Alert>
		</Snackbar>
		
		<Snackbar open={showToastErrorSave} onClose={() => setShowToastErrorSave(false)} autoHideDuration={3000}>
		  <Alert severity="error">Error Saving Query</Alert>
		</Snackbar>

		<Snackbar open={showToastErrorDelete} onClose={() => setShowToastErrorDelete(false)} autoHideDuration={3000}>
		  <Alert severity="error">Error Deleting Query</Alert>
		</Snackbar>

	  </div>
	  
	</div>
  );
  
}

