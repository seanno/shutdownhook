
import React, { useEffect, useState } from 'react';
import { Button, List, ListItem, ListItemButton, ListItemText, ListSubheader, MenuItem, Select } from '@mui/material';
import { serverFetchQueries, serverFetchQuery } from './lib/server.js';

import Editor from './Editor.js';
import Runner from './Runner.js';
import SchemaViewer from './SchemaViewer.js';
import styles from './Navigator.module.css';

export default function Navigator() {

  const [queryTree, setQueryTree] = useState(undefined);
  const [connection, setConnection] = useState(undefined);
  const [query, setQuery] = useState(undefined);
  const [selectedQuery, setSelectedQuery] = useState(undefined);
  const [runningQuery, setRunningQuery] = useState(undefined);
  const [viewSchema, setViewSchema] = useState(false);

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
	  const currentConnectionName =
			(connection ? connection.Name : localStorage.getItem("connection"));
	  
	  if (currentConnectionName) {
		const newConnection = findConnection(tree, currentConnectionName);
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
	setQueryRunnerView(undefined, viewSchema);
	setQuery({
	  'ConnectionName': connection.Name,
	  'Description': 'New Query',
	  'Owner': queryTree.User,
	  'IsShared': false,
	  'Statement': ''
	});
  }

  function editQuery(queryId) {
	setQueryRunnerView(undefined, viewSchema);
	serverFetchQuery(queryId).then((q) => setQuery(q));
  }
  
  function runQuery(queryId) {
	serverFetchQuery(queryId).then((q) => setQueryRunnerView(q, false));
  }

  function closeEditor(refreshTree) {
	if (refreshTree) setQueryTree(undefined);
	setQueryRunnerView(undefined, false);
	setQuery(undefined);
  }

  function runFromEditor(q) {
	setQueryRunnerView(q, false);
  }

  function viewSchemaClick() {
	setQueryRunnerView(undefined, true);
  }

  // +-------------------+
  // | renderQueryRunner |
  // +-------------------+

  function setQueryRunnerView(q, schema) {
	setRunningQuery(q);
	setViewSchema(schema);
  }
  
  function renderQueryRunner() {
	return(
	  <div className={styles.runnerContent}>
		{ runningQuery && <Runner query={runningQuery} /> }
		{ viewSchema && connection && <SchemaViewer connectionName={connection.Name} /> }
	  </div>
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

	const ownedQueries = connection.Queries.filter((qi) => (queryTree.User === qi.Owner));
	const sharedQueries = connection.Queries.filter((qi) => (queryTree.User !== qi.Owner));
	const showLabels = (ownedQueries.length > 0 && sharedQueries.length > 0);
	
	return(
	  <>	
		<div className={styles.navLabel + ' ' + styles.queriesLabel}>Queries:</div>
		<div className={styles.queriesList}>
		  <List
			dense={true}
			disablePadding={true}
			style={{ maxHeight: '100%', overflow: 'auto' }} >

			{renderQueriesSubset(ownedQueries, showLabels, true)}
			{renderQueriesSubset(sharedQueries, showLabels, false)}
		  </List>
		</div>
		<div className={styles.queriesButtons}>
		  
		  { connection.CanCreate &&
			<Button variant="outlined" onClick={newQuery}>New</Button> }
		  
		  { selectedQuery &&
			<Button variant="outlined" onClick={() => runQuery(selectedQuery.Id)}>Run</Button> }
		  
		  { selectedQuery && (selectedQuery.Owner === queryTree.User) &&
			<Button variant="outlined" onClick={() => editQuery(selectedQuery.Id)}>Edit</Button> }

		  { connection.CanCreate &&
			<Button variant="outlined" onClick={viewSchemaClick}>Schema</Button> }

		</div>
	  </>
	);
  }

  function renderQueriesSubset(queries, showLabel, owned) {
	
	if (queries.length === 0) return(undefined);

	const label = (showLabel ? (owned ? "--- Owned" : "--- Shared") : undefined);
	
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
		{ label && <ListSubheader style={{ lineHeight: 'normal' }}>{label}</ListSubheader> }
		{ elts }
	  </>
	);

  }

  // +-------------------------+
  // | renderConnectionChooser |
  // +-------------------------+

  function connectionChange(evt) {

	const newConnection = findConnection(queryTree, evt.target.value);
	setConnection(newConnection);
	localStorage.setItem("connection", newConnection.Name);
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

	  { (runningQuery || viewSchema) && renderQueryRunner() }
	</div>
  );
  
}

