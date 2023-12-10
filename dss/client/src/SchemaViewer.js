
import React, { useEffect, useState } from 'react';
import { serverGetSchema } from './lib/server.js';

import ResultsTables from './ResultsTables.js';

export default function SchemaViewer({ connectionName }) {

  const [results, setResults] = useState(undefined);

  // +---------+
  // | Effects |
  // +---------+

  useEffect(() => {

	const fetchResults = async () => {

	  try {
		const results = await serverGetSchema(connectionName);
		
		if (results.Error) {
		  setResults(undefined);
		  console.error(results.Error);
		}
		else {
		  setResults(results);
		}
	  }
	  catch (err) {
		setResults(undefined);
		console.error(`unexpected: ${err}`);
	  }
	}
	
	fetchResults();
	
  }, [connectionName]);

  // +-------------+
  // | Main Render |
  // +-------------+

  return(results && <ResultsTables results={results} />);
}

