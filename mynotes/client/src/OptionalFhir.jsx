
// NOTE: This approach heavily derived from
// https://github.com/zeevo/react-fhirclient under MIT license

import React, { useState, useEffect } from 'react';
import FHIR from 'fhirclient';

const OptionalFhirContext = React.createContext(undefined);

export default function OptionalFhir({ children }) {

  const [fhir, setFhir] = useState();

  useEffect(() => {

	// quick exit if we're not coming back from fhir auth
	const params = new URLSearchParams(document.location.search);
	const code = params.get('code');
	if (!code) return;

	// stop strict mode from double-fetching the token
	if (code === window.lastCode) return;
	window.lastCode = code;

	const authClient = async () => {
	  FHIR.oauth2.ready().then(client => setFhir(client));
	};

	authClient().catch(console.error);
	
  }, []); // empty array as second param ensures we'll only run once
  
  return (
	<OptionalFhirContext.Provider value={fhir}>
	  {children}
	</OptionalFhirContext.Provider>
  );
}

export function useOptionalFhir() {
  return(React.useContext(OptionalFhirContext));
}
