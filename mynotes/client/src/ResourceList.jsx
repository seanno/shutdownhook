import { useEffect, useState } from 'react'
import { List, ListItem, ListItemButton, ListItemText } from '@mui/material';

import { fetchAll } from './lib/fhirCalls.js';
import { getResourceHandler } from './lib/resourceHandlers.js';

import styles from './App.module.css'

export default function ResourceList({ fhir, resourceType, filterParams, selectedIndex, onClick }) {

  const [resources, setResources] = useState(undefined);
  const [error, setError] = useState(undefined);

  const resourceHandler = getResourceHandler(resourceType);
  
  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	const loadResources = async () => {
	  fetchAll(fhir, resourceType, filterParams)
		.then(result => {
		  result.sort((a,b) => resourceHandler.compare(a,b));
		  setResources(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('error loading resources of type: ' + resourceType);
		});
	}

	loadResources();

	return(() => {
	  setResources(undefined);
	  setError(undefined);
	});
	
  }, [resourceType, filterParams]);

  // +-----------------+
  // | renderResources |
  // +-----------------+

  function labelForResourceType(resourceType) {
	switch (resourceType) {
	  case 'Encounter': return('visits');
	  case 'DocumentReference': return('notes');
	  default: return(resourceType + 's');
	}
  }
  
  function renderResources() {

	if (resources.length == 0) {
	  return(<div>No {labelForResourceType(resourceType)} found</div>);
	}
	
	const items = resources.map((r, i) => {

	  const key = `${resourceType}-${i}`;
	  const primary = resourceHandler.primaryText(r);
	  const secondary = resourceHandler.secondaryTexts(r).map((t,j) => <span key={`${key}-${j}`}>{t}<br/></span>);

	  return(
		<ListItem key={key} dense={true} disablePadding>
		  <ListItemButton selected={selectedIndex === i} onClick={() => onClick(r, i)}>
			<ListItemText primary={primary} secondary={secondary} />
		  </ListItemButton>
		</ListItem>
	  );
	  
	});
	
	return(
	  <div>
		<List>
		  {items}
		</List>
	  </div>
	);
  }

  // +---------------+
  // | renderMessage |
  // +---------------+

  function renderMessage(msg, isError) {
	return(
	  <div className={ isError ? styles.msg : styles.err }>{msg}</div>
	);
  }

  // +--------+
  // | render |
  // +--------+
  
  return(
	<>
	  { error  && renderMessage(error, true) }
	  { !error && !resources && renderMessage('loading...') }
	  { !error && resources && renderResources() }
	</>
  );
}

