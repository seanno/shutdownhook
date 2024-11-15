import { useState } from 'react'
import FacilityPicker from './FacilityPicker.jsx';
import Intro from './Intro.jsx';
import Explain from './Explain.jsx';

import { Button, Checkbox, ListItem,
		 ListItemButton, ListItemIcon,
		 ListItemText } from '@mui/material';

import styles from './App.module.css'

export default function Home({ incomingText }) {

  const [selectedEndpoint, setSelectedEndpoint] = useState(undefined);
  const [checkedOK, setCheckedOK] = useState(false);
  const [showExplain, setShowExplain] = useState(false);
  
  // +---------+
  // | actions |
  // +---------+

  function okToLaunch() {
	return(selectedEndpoint && checkedOK);
  }

  function launch() {
	
	const url = 'launch.html?client=' +
		  encodeURIComponent(selectedEndpoint.clientId) +
		  '&iss=' + encodeURIComponent(selectedEndpoint.iss);

	window.location = url;
  }

  function okToPaste() {
	return(checkedOK);
  }

  function pasteExplain() {
	setShowExplain(true);
  }

  // +------------------------+
  // | renderIncomingControls |
  // +------------------------+

  function renderIncomingControls() {

	return(
	  <div>
		<p>
		  <b>By clicking below, you acknowledge that you've read, understood and
		   agreed to all the words on this page.</b>
		</p>
		<Button
		  variant='contained'
		  onClick={pasteExplain}>
		  Agreed, please explain my text
		</Button>
	  </div>
	);
  }

  // +----------------+
  // | renderControls |
  // +----------------+

  function renderControls() {

	return(
	  <div>
		<FacilityPicker setSelectedEndpoint={setSelectedEndpoint} />
		
		<ListItem disablePadding>
		  <ListItemButton
			rule={undefined}
			onClick={() => setCheckedOK(!checkedOK)}>
			<ListItemIcon sx={{ minWidth: '20px' }}>
			  <Checkbox
				edge='start'
				checked={checkedOK}
				tabIndex={-1}
				disableRipple
				inputProps={{ 'aria-labelledby': 'checkedOKLabel' }}
				sx={{ padding: '0px' }}
			  />
			</ListItemIcon>

			<ListItemText
			  sx={{ margin: '0px' }}
			  id='checkedOKLabel'
			  primary='I have read, understood and accepted all the words on this page'
			/>
		  </ListItemButton>
		</ListItem>
		
		<Button
		  variant='contained'
		  sx={{ mt: 1, mr: 1 }}
		  disabled={!okToLaunch()}
		  onClick={launch}>
		  Connect
		</Button>
		
		<Button
		  variant='contained'
		  sx={{ mt: 1 }}
		  disabled={!okToPaste()}
		  onClick={pasteExplain}>
		  Or, paste notes from any document
		</Button>
	  </div>
	);
  }
  
  // +-------------+
  // | Main Render |
  // +-------------+
  
	return(
	  <div className={styles.content}>
		
		{ incomingText && renderIncomingControls() }
		{ !incomingText && renderControls() }
		
		<Intro />
		
		{ showExplain && !incomingText &&
		  <Explain onClose={() => setShowExplain(false) } /> }
		
		{ showExplain && incomingText &&
		  <Explain initialText={incomingText} onClose={() => window.location='/' } /> }
		
	  </div>
	);
}

