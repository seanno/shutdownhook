import { Fragment, useState } from 'react'

import styles from './App.module.css'

export default function FacilityPicker() {

  const facilities = [
	{
	  name: 'Epic',
	  clientId: '253caf2d-1c87-4249-9043-3872e2a73df5',
	  endpoints: [
		{
		  name: 'Overlake Hospital Medical Center',
		  iss: 'https://sfd.overlakehospital.org/FHIRproxy/api/FHIR/R4/'
		},
		{
		  name: 'Mass General Brigham',
		  iss: 'https://ws-interconnect-fhir.partners.org/Interconnect-FHIR-MU-PRD/api/FHIR/R4/'
		}
	  ]
	},
	{
	  name: 'Epic Sandbox',
	  clientId: '254d564d-40fd-4296-b292-6933962371b9',
	  endpoints: [
		{
		  name: 'Epic Sandbox',
		  iss: 'https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4/'
		}
	  ]
	},
	{
	  name: 'Cerner Sandbox',
	  clientId: 'faa3ae50-ca8d-482d-bf20-a2d41e4f2748',
	  endpoints: [
		{
		  name: 'Cerner Sandbox',
		  iss: 'https://fhir-myrecord.cerner.com/r4/ec2458f2-1e24-41c8-b71b-0e701af7583d'
		}
	  ]
	},
	{
	  name: 'Smart Launcher',
	  clientId: '6fc59f34-15b6-434b-beff-e0dd0750be5e',
	  endpoints: [
		{
		  name: 'Smart Launcher',
		  iss: 'https://launch.smarthealthit.org/v/r4/sim/WzMsIiIsIiIsIkFVVE8iLDAsMCwwLCIiLCIiLCIiLCIiLCIiLCIiLCIiLDAsMSwiIl0/fhir'
		}
	  ]
	}
  ];

  const links = [];
  for (var i = 0; i < facilities.length; ++i) {
	const ehr = facilities[i];
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

