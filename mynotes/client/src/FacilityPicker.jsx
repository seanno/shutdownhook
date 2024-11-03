import { Fragment, useState } from 'react'
import { endpoints } from './lib/endpoints.js';

import styles from './App.module.css'

export default function FacilityPicker() {

  const links = [];
  for (var i = 0; i < endpoints.length; ++i) {
	const ehr = endpoints[i];
	const client = encodeURIComponent(ehr.clientId);
	for (var j = 0; j < ehr.endpoints.length; ++j) {
	  const endpoint = ehr.endpoints[j];
	  const iss = encodeURIComponent(endpoint.iss);
	  const url = `launch.html?client=${client}&iss=${iss}`;
	  
	  links.push(
		<Fragment key={`fblink-${i}-${j}`}>
		  <a href={url}>{endpoint.name}</a>
		  <br/>
		</Fragment>
	  );
	}
  }
  
  return(
	<div className={styles.content}>
	  <div>
		{links}
	  </div>
	</div>
  );
}

