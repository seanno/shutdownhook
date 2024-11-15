import { useState } from 'react'
import { useOptionalFhir } from './OptionalFhir.jsx';
import AppHeader from './AppHeader.jsx';
import Home from './Home.jsx';
import Reader from './Reader.jsx';

import styles from './App.module.css'

export default function App() {

  const fhir = useOptionalFhir();
  const codeInQuery = (document.location.search.indexOf("code=") !== -1);

  var incomingText = document.location.hash;
  if (incomingText) {
	if (incomingText.startsWith('#')) incomingText = incomingText.substring(1);
	incomingText = decodeURIComponent(incomingText);
  }
  
  return (
	
	<div className={styles.body}>
	  
	  <div className={styles.header}>
		<AppHeader fhir={fhir} />
	  </div>

	  { !fhir && !codeInQuery && <Home incomingText={incomingText} /> }
	  { fhir && <Reader fhir={fhir} /> }
	  
    </div>
  )
}

