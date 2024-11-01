import { useState } from 'react'
import { useOptionalFhir } from './OptionalFhir.jsx';
import AppHeader from './AppHeader.jsx';
import FacilityPicker from './FacilityPicker.jsx';
import Reader from './Reader.jsx';

import styles from './App.module.css'

export default function App() {

  const fhir = useOptionalFhir();
  const codeInQuery = (document.location.search.indexOf("code=") !== -1);
  
  return (
	
	<div className={styles.body}>
	  
	  <div className={styles.header}>
		<AppHeader fhir={fhir} />
	  </div>
	  
	  { !fhir && !codeInQuery && <FacilityPicker /> }
	  { fhir && <Reader fhir={fhir} /> }
	  
    </div>
  )
}

