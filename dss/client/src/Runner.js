
import React, { useEffect, useState } from 'react';
import { Button,TextField } from '@mui/material';
import { parseParams, paramDefaults, serverRunQuery } from './lib/server.js';

import styles from './Runner.module.css';

export default function Runner({ query }) {

  const [runQuery, setRunQuery] = useState(undefined);
  const [paramValues, setParamValues] = useState(undefined);
  
  const [refreshNow, setRefreshNow] = useState(false);

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
	}

	fetchResults();
  });

  // +---------+
  // | Buttons |
  // +---------+

  function refreshClick() {
	setRefreshNow(true);
  }

  function renderButtons() {

	const divStyle = (paramValues ? styles.param : styles.params);

	return(
	  <div className={divStyle}>
		<Button variant="outlined"
				disabled={missingParams()}
				onClick={refreshClick}>Refresh</Button>
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

	if (!paramValues) {
	  return(renderButtons());
	}

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
		{renderButtons()}
	  </div>
	);
  }
  
  // +--------------+
  // | renderResult |
  // +--------------+

  function renderResult(result, index) {

	if (!result.Rows || result.Rows.length === 0) {
	  
	  const msg = result.UpdateCount
			? `${result.UpdateCount} row${result.UpdateCount > 1 ? 's' : ''} affected`
			: 'no results';
	
	  return(<div key={`res${index}`} className={styles.updateResult}>{msg}</div>);
	}
	
	const headerCells = result.Headers.map((hdr, ihdr) => {
	  return(
		<th key={`hdr-${ihdr}`}>{hdr}</th>
	  );
	});
											
	const bodyRows = result.Rows.map((r, irow) => {
	  return(
		<tr key={`row-${irow}`}>
		  { r.map((cell, icell) => <td key={`cell-${icell}`}>{cell}</td>) }
		</tr>
	  );
	});

	return(
	  <table key={`res${index}`} className={styles.results}>
		<thead><tr>{ headerCells }</tr></thead>
		<tbody>{ bodyRows }</tbody>
	  </table>
	);
  }

  function renderResults() {
	
	if (!results.Results || results.Results.length === 0) {
	  return(<div className={styles.updateResult}>no results</div>);
	}

	//console.log(JSON.stringify(results, null, 2));
	const elts = results.Results.map(renderResult);
	return(<>{elts}</>);
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

	  { results && renderResults() }
	  { error && renderError() }
	  
	</div>
  );
  
}

