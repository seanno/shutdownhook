import { useEffect, useState } from 'react'

import ResourceList from './ResourceList.jsx';
import DocumentView from './DocumentView.jsx';
import { filterByEncounter } from './lib/fhirCalls.js';

import styles from './App.module.css'

export default function Reader({ fhir }) {

  const [selectedEncounterIndex, setSelectedEncounterIndex] = useState(undefined);
  const [selectedEncounter, setSelectedEncounter] = useState(undefined);

  const [selectedDocumentIndex, setSelectedDocumentIndex] = useState(undefined);
  const [selectedDocument, setSelectedDocument] = useState(undefined);

  // +---------+
  // | Actions |
  // +---------+

  function onEncounterClick(encounter, index) {
	
	if (index === selectedEncounterIndex) return;

	setSelectedEncounter(encounter);
	setSelectedEncounterIndex(index);

	setSelectedDocument(undefined);
	setSelectedDocumentIndex(undefined);
  }

  function onDocumentClick(document, index) {
	
	if (index === selectedDocumentIndex) return;

	setSelectedDocument(document);
	setSelectedDocumentIndex(index);
  }

  // +-------------+
  // | Main Render |
  // +-------------+

  return(
	<>

	  <div className={styles.nav} style={{ gridColumn: 2 }}>
		<ResourceList
		  fhir={fhir}
		  resourceType='Encounter'
		  selectedIndex={selectedEncounterIndex}
		  onClick={onEncounterClick} />
	  </div>

	  { selectedEncounter &&
		<div className={styles.nav} style={{ gridColumn: 3 }}>
		  <ResourceList
			fhir={fhir}
			resourceType='DocumentReference'
			filterParams={filterByEncounter(fhir, selectedEncounter.id)}
			selectedIndex={selectedDocumentIndex}
			onClick={onDocumentClick} />
		</div>
	  }

	  { selectedDocument &&
		<div className={styles.nav} style={{ gridColumn: 4 }}>
		  <DocumentView
			fhir={fhir}
			doc={selectedDocument} />
		</div>
	  }

	</>
  );
}

