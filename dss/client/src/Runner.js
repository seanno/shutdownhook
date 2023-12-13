
import React, { useEffect, useState } from 'react';
import { Button, CircularProgress, TextField } from '@mui/material';
import { b64uEncode, toCSV, toCSVLine, saveToFile } from './lib/util.js';
import { parseParams, paramDefaults, serverRunQuery } from './lib/server.js';

import ResultsTables from './ResultsTables.js';

import styles from './Runner.module.css';

export default function Runner({ query }) {

  const [runQuery, setRunQuery] = useState(undefined);
  const [paramValues, setParamValues] = useState(undefined);
  
  const [refreshNow, setRefreshNow] = useState(false);
  const [running, setRunning] = useState(false);

  const [results, setResults] = useState(undefined);
  const [error, setError] = useState(undefined);

  // +---------+
  // | Effects |
  // +---------+

  useEffect(() => {

	const vals = paramDefaults(query.ParamsCsv);

	setRunQuery(query);
	setParamValues(vals);

	setResults(undefined);
	setError(undefined);

	if (!vals || vals.filter((v) => !v || v === '').length === 0) {
	  setRefreshNow(true);
	}
	
  }, [query]);

  // eslint-disable-next-line
  useEffect(() => {

	if (!refreshNow) return;
	setRefreshNow(false);
	setRunning(true);
	
	const fetchResults = async () => {

	  const runQueryInfo = {
		Connection: runQuery.ConnectionName
	  };

	  if (runQuery.Id) runQueryInfo.QueryId = runQuery.Id;
	  if (runQuery.Statement) runQueryInfo.Statement = runQuery.Statement;
	  if (paramValues) runQueryInfo.Params = paramValues;

	  try {
		const results = await serverRunQuery(runQueryInfo);
		
		if (results.Error) {
		  setResults(undefined);
		  setError(results.Error);
		}
		else {
		  setResults(results);
		  setError(undefined);
		}
	  }
	  catch (err) {
		setResults(undefined);
		setError(`unexpected: ${err}`);
	  }
	  finally {
		setRunning(false);
	  }
	}

	fetchResults();
  });

  // +---------+
  // | Buttons |
  // +---------+

  function refreshClick() {
	setRefreshNow(true);
  }

  function csvClick() {

	let csv = '';

	results.Results.forEach((r) => {
	  if (!r.Rows || r.Rows.length === 0) return;
	  csv += toCSVLine(r.Headers) + '\n' + toCSV(r.Rows) + '\n';
	});

	const fileName = runQuery.Description.replace(/[^a-zA-Z0-9]/g, '_') + '.csv';
	
	saveToFile(fileName, csv, 'text/csv');
  }

  function urlClick() {

	let url = window.location.protocol + '//' + window.location.hostname;
	if (window.location.port) url += ':' + window.location.port;

	const urlQuery = {
	  ConnectionName: runQuery.ConnectionName,
	  Id: runQuery.Id,
	  Description: runQuery.Description,
	  Statement: runQuery.Statement
	}

	if (paramValues) {

	  const params = parseParams(runQuery.ParamsCsv);
	  let newParams = ''
	  
	  for (const i in paramValues) {
		if (i > 0) newParams += ',';
		newParams += params[i].Name + ':' + paramValues[i];
	  }

	  urlQuery.ParamsCsv = newParams;
	}

	url += '/?query=' + b64uEncode(JSON.stringify(urlQuery));
	
	window.open(url);
  }

  function renderButtons() {

	return(
	  <div className={styles.buttons}>
		<Button variant="outlined"
				disabled={missingParams()}
				onClick={refreshClick}>Refresh</Button>

		{ results && <Button variant="outlined"
							 onClick={csvClick}>Save as CSV</Button> }

		{ results && <Button variant="outlined"
							 onClick={urlClick}>Open as URL</Button> }
	  </div>
	);
  }
  
  // +------------+
  // | Parameters |
  // +------------+

  function missingParams() {
	return(paramValues && paramValues.filter((v) => !v || v === '').length > 0);
  };

  function updateParamValues(i, value) {
	const newParamValues = paramValues.slice();
	newParamValues[i] = value;
	setParamValues(newParamValues);
  }

  function renderParamInputs() {

	if (!paramValues) return;

	const elts = parseParams(runQuery.ParamsCsv).map((p, index) => {
	  return(
		<React.Fragment key={`inp${index}`}>
		  <div className={styles.label}>
			{p.Name}:
		  </div>
		  <div className={styles.param}>
			<TextField
			  value={paramValues[index]}
			  variant='outlined'
			  size='small'
			  onChange={ (evt) => updateParamValues(index, evt.target.value) }
			  inputProps={{ style: { paddingTop: '1px', paddingBottom: '1px' } }}
			/>
		  </div>
		</React.Fragment>
	  );
	});

	return(
	  <div className={styles.params}>
		{elts}
	  </div>
	);
  }
  
  // +-------------+
  // | renderError |
  // +-------------+

  function renderError() {
	return(<div className={styles.error}>{error}</div>);
  }

  // +-------------+
  // | Main Render |
  // +-------------+

  if (!runQuery) return(undefined);

  return (
	<div className={styles.container}>
	  
	  <h1>{runQuery.Description}</h1>
	  
	  { renderParamInputs() }
	  { renderButtons() }

	  { running && <CircularProgress sx={{ margin: '12px' }} /> }
	  { results && <ResultsTables results={results} /> }
	  { error && renderError() }
	  
	</div>
  );
  
}

