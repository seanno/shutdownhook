import { useEffect, useState } from 'react'
import { Autocomplete, CircularProgress, TextField } from '@mui/material';
import { filter } from './lib/server.js';
  
export default function FacilityPicker({ setSelectedEndpoint }) {

  const [inputText, setInputText] = useState('');
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(false);
  
  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	if (!inputText || inputText.length < window.gramLength) {
	  setOptions([]);
	  return;
	}

	setLoading(true);

	const getOptions = async () => {
	  try {
		const options = await filter(inputText);
		setOptions(options);
	  }
	  catch (err) {
		console.error(`getOptions: ${err}`);
		setOptions([]);
	  }
	  finally {
		setLoading(false);
	  }
	};

	getOptions();
	
  }, [inputText]);

  // +-----------+
  // | debouncer |
  // +-----------+

  var timerId = undefined;

  function debounceInputChange(newValue) {

	if (timerId) clearTimeout(timerId);

	timerId = setTimeout(() => setInputText(newValue),
						 window.debounceMillis);
  }

  // +-------------+
  // | Main Render |
  // +-------------+
  
  return(
	<Autocomplete
	  
	  disablePortal
	  options={ options }
	  loading={ loading }
	  
	  onInputChange={ (evt, newValue) => {
		debounceInputChange(newValue);
	  }}
	  
	  onChange={ (evt, newValue) => {
		setSelectedEndpoint(newValue)
	  }}
	  
	  renderInput={ (params) => (
		<TextField
		  {...params}
		  autoFocus
		  label="Choose a Provider"
		  InputProps={{
			...params.InputProps,
			endAdornment: (
			  <>
				{ loading ? <CircularProgress color="inherit" size={20} /> : undefined }
				{ params.InputProps.endAdornment }
			  </>
			)
		  }}
		/>
	  )}
	  
	/>
  );
}

