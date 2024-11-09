import { useState } from 'react'
import { IconButton } from '@mui/material';
import LogoutIcon from '@mui/icons-material/Logout';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';

export default function AppHeader({ fhir }) {

  return (
	<>
	  
	  <img src="explain.png" />
	  
	  <br/><br/>
	  
	  { fhir &&
		<>
		  <a href="/">
			<IconButton aria-label="logout">
			  <LogoutIcon style={{ transform: 'scaleX(-1)' }} />
			</IconButton>
		  </a>
		  <br/>
		</>
	  }

	  <a target="_blank" href="https://shutdownhook.com/explain-my-notes/">
		<IconButton aria-label="about">
		  <HelpOutlineIcon />
		</IconButton>
	  </a>
    </>
  )
}

