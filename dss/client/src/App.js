
import { b64uDecode } from './lib/util.js';

import Navigator from './Navigator.js';
import Runner from './Runner.js';

import styles from './App.module.css';

export default function App() {

  function checkForUrlQuery() {
	
	const queryParams = new URLSearchParams(document.location.search);
	const queryJson = queryParams.get('query');

	return(queryJson ? JSON.parse(b64uDecode(queryJson)) : undefined);
  }

  const urlQuery = checkForUrlQuery();
  
  return (
    <div className={styles.container}>
	  { urlQuery && <Runner query={urlQuery} /> }
	  { !urlQuery && <Navigator /> }
    </div>
  );
  
}

