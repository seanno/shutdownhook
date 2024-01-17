
import { Link } from '@mui/material';
import { b64uDecode } from './lib/util.js';

import Navigator from './Navigator.js';
import Runner from './Runner.js';

import styles from './App.module.css';

export default function App() {

  function logoutClick() {
	const redirectUrl = encodeURIComponent(window.location.pathname +
										   window.location.search);

	window.location = '/__logout?r=' + redirectUrl;
  }
  
  function checkForUrlQuery() {
	
	const queryParams = new URLSearchParams(document.location.search);
	const queryJson = queryParams.get('query');

	return(queryJson ? JSON.parse(b64uDecode(queryJson)) : undefined);
  }

  const urlQuery = checkForUrlQuery();
  
  return (
    <div className={styles.container}>
	  <div className={styles.logout}>
		<a target="_blank" rel="noreferrer" 
		   href="https://github.com/seanno/shutdownhook/tree/main/dss#readme">about</a>
		<br/>
		<Link component="button" onClick={logoutClick}>logout</Link>
	  </div>
	  { urlQuery && <Runner query={urlQuery} /> }
	  { !urlQuery && <Navigator /> }
    </div>
  );
  
}

