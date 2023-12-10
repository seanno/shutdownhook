
import React, { useEffect, useState } from 'react';
import { Button, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select } from '@mui/material';
import { serverFetchQueries, serverFetchQuery } from './lib/server.js';

import Editor from './Editor.js';
import Runner from './Runner.js';
import styles from './Navigator.module.css';

export default function Navigator() {

  const [queryTree, setQueryTree] = useState(undefined);
  const [connection, setConnection] = useState(undefined);
  const [query, setQuery] = useState(undefined);
  const [selectedQuery, setSelectedQuery] = useState(undefined);
  const [runningQuery, setRunningQuery] = useState(undefined);

  // +---------+
  // | Effects |
  // +---------+

  function findConnection(tree, name) {

	if (tree === undefined || tree.Connections === undefined) {
	  return(undefined);
	}

	for (const i in tree.Connections) {
	  if (tree.Connections[i].Name === name) {
		return(tree.Connections[i]);
	  }
	}

	return(undefined);
  }
  
  useEffect(() => {

	if (queryTree !== undefined) return;

	serverFetchQueries().then((tree) => {
	  
	  setQueryTree(tree);
	  setSelectedQuery(undefined);

	  // if connection previously set, try to find it
	  if (connection !== undefined) {
		const newConnection = findConnection(tree, connection.Name);
		if (newConnection !== undefined) {
		  setConnection(newConnection);
		  return;
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

	return(
	  <>	
		<div className={styles.navLabel + ' ' + styles.queriesLabel}>Queries:</div>
		<div className={styles.queriesList}>
		  <List
			dense={true}
			disablePadding={true}
			style={{ maxHeight: '100%', overflow: 'auto' }} >

			{renderQueriesSubset(true)}
			{renderQueriesSubset(false)}
		  </List>
		</div>
		<div className={styles.queriesButtons}>
		  
		  { connection.CanCreate &&
			<Button variant="outlined" onClick={newQuery}>New</Button> }
		  
		  { selectedQuery &&
			<Button variant="outlined" onClick={() => runQuery(selectedQuery.Id)}>Run</Button> }
		  
		  { selectedQuery && (selectedQuery.Owner === queryTree.User) &&
			<Button variant="outlined" onClick={() => editQuery(selectedQuery.Id)}>Edit</Button> }

		</div>
	  </>
	);
  }

  function renderQueriesSubset(owned) {
	
	const label = (owned ? '--- Owned' : '--- Shared');
	
	const queries = connection.Queries.filter((qi) => {
	  return((owned && queryTree.User === qi.Owner) ||
			 (!owned && queryTree.User !== qi.Owner));
	});

	if (queries.length === 0) return(undefined);

	const elts = queries.map((qi) => {
	  
	  const primaryText = qi.Description + (owned && qi.IsShared ? ' *' : '');
	  const hoverText = `owned by ${qi.Owner}` + (qi.IsShared ?  ' (shared)' : '');

	  return(
		<ListItem key={`qry${qi.Id}`}>
		  <ListItemButton
			selected={qi === selectedQuery}
			onClick={ () => setSelectedQuery(qi) }
			style={{padding: '0px', lineHeight: 'normal' }} >

			<ListItemText
			  primary={primaryText}
			  title={hoverText}
			  style={{paddingLeft: '12px', lineHeight: 'normal' }}
			  />
		  </ListItemButton>
		</ListItem>
	  )
	});

	return(
	  <>
		<ListSubheader style={{ lineHeight: 'normal' }}>{label}</ListSubheader>
		{elts}
	  </>
	);

  }

  // +-------------------------+
  // | renderConnectionChooser |
  // +-------------------------+

  function connectionChange(evt) {

	const newConnection = findConnection(queryTree, evt.target.value);
	setConnection(newConnection);
  }
  
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
		  <Select
			size="small"
			onChange={(evt) => { connectionChange(evt); } }
			value={connection.Name}>
			
			{ elts }
		  </Select>
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

