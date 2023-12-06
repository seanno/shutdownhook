
import React, { useEffect, useState } from 'react';
import { Link, MenuItem, Select } from '@mui/material';
import { serverFetchQueries, serverFetchQuery } from './lib/server.js';

import Editor from './Editor.js';
import Runner from './Runner.js';
import styles from './Navigator.module.css';

export default function Navigator() {

  const [queryTree, setQueryTree] = useState(undefined);
  const [connection, setConnection] = useState(undefined);
  const [query, setQuery] = useState(undefined);
  const [runningQuery, setRunningQuery] = useState(undefined);

  // +---------+
  // | Effects |
  // +---------+

  useEffect(() => {

	if (queryTree !== undefined) return;
	
	serverFetchQueries().then((tree) => {
	  
	  setQueryTree(tree);

	  // if connection previously set, try to find it
	  if (connection !== undefined) {
		for (const i in tree.Connections) {
		  if (tree.Connections[i].Name === connection.Name) {
			setConnection(tree.Connections[i]);
			return;
		  }
		}
	  }

	  // oh well, default to first in list
	  if (tree.Connections.length > 0) {
		setConnection(tree.Connections[0]);
		return;
	  }

	  // bummah
	  setConnection(undefined);
	  
	});
	  
  }, [connection, queryTree]);

  // +--------+
  // | Clicks |
  // +--------+

  function newQuery() {
	setRunningQuery(undefined);
	setQuery({
	  'ConnectionName': connection.Name,
	  'Description': 'New Query',
	  'Owner': queryTree.User,
	  'IsShared': false,
	  'Statement': ''
	});
  }

  function editQuery(queryId) {
	setRunningQuery(undefined);
	serverFetchQuery(queryId).then((q) => setQuery(q));
  }

  function runQuery(queryId) {
	serverFetchQuery(queryId).then((q) => setRunningQuery(q));
  }

  function closeEditor(refreshTree) {
	if (refreshTree) setQueryTree(undefined);
	setRunningQuery(undefined);
	setQuery(undefined);
  }

  function runFromEditor(q) {
	setRunningQuery(q);
  }

  // +-------------------+
  // | renderQueryRunner |
  // +-------------------+

  function renderQueryRunner() {
	return(
	  <>
		<div className={styles.runnerContent}>
		  <Runner query={runningQuery} />
		</div>
	  </>
	);
  }

  // +-------------------+
  // | renderQueryEditor |
  // +-------------------+

  function renderQueryEditor() {
	return(
	  <>
		<div className={styles.navLabel + ' ' + styles.editorLabel}>Query:</div>
		
		<div className={styles.editorContent}>
		  <Editor query={query} closeFn={closeEditor} runFn={runFromEditor} />
		</div>
	  </>
	);
  }

  // +-----------------+
  // | renderQueryList |
  // +-----------------+

  function renderQueryList() {
	
	if (queryTree === undefined || connection === undefined) {
	  return(undefined);
	}

	const owned = renderQueriesSubset(true);
	const shared = renderQueriesSubset(false);

	return(
	  <>
		<div className={styles.navLabel + ' ' + styles.queriesLabel}>Queries:</div>
		{ owned }
		{ shared }
	  </>
	);
  }

  function renderQueriesSubset(owned) {
	
	const label = (owned ? 'Owned' : 'Shared');
	
	const className = styles.queriesList + ' ' +
		  (owned ? styles.queriesOwned : styles.queriesShared);
	
	const queries = connection.Queries.filter((qi) => {
	  return((owned && queryTree.User === qi.Owner) ||
			 (!owned && queryTree.User !== qi.Owner));
	});

	if (!owned && queries.length === 0) return(undefined);

	const queryLinks = queries.map((qi) => {
	  
	  const star = (owned && qi.IsShared ? " *" : "");
	  const hover = 'owned by ' + qi.Owner + (qi.IsShared ? '; shared' : '');
	  const func = (owned ? editQuery : runQuery);
	  
	  return(
		<li key={qi.Id}>
		  <Link className={styles.queryLink}
				component='button'
				title={hover}
				onClick={() => func(qi.Id) }>
			{qi.Description}
		  </Link>
		  { star }
		</li>
	  )
	});

	return(
	  <div className={className}>

		<div className={styles.queriesHdr}>{label}</div>
		<ul>
		  { owned && connection.CanCreate &&
			<li>
			  <Link className={styles.queryLink}
					component='button'
					onClick={() => newQuery() }>
				Create New
			  </Link>
			</li> }
		  { queryLinks }
		</ul>
	  </div>
	);

  }

  // +-------------------------+
  // | renderConnectionChooser |
  // +-------------------------+

  function renderConnectionChooser() {

	if (queryTree === undefined || queryTree.Connections.length === 0) {
	  return(undefined);
	}

	const elts = queryTree.Connections.map((ci) => {
	  return(<MenuItem key={ci.Name} value={ci.Name}>{ci.Description}</MenuItem>);
	});
		  
	return(
	  <>
		<div className={styles.navLabel + ' ' + styles.connectionLabel}>Connection:</div>
		<div className={styles.connectionSelect}>
		  <Select size="small" value={connection.Name}>{ elts }</Select>
		</div>
	  </>
	);
  }

  // +-------------+
  // | Main Render |
  // +-------------+

  return (
	<div className={styles.container}>
	  { renderConnectionChooser() }
	  { !query && renderQueryList() }
	  { query && renderQueryEditor() }
	  { runningQuery && renderQueryRunner() }
	</div>
  );
  
}

