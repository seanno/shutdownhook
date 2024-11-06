
import { b64_to_arr } from './b64.js';

// +------------+
// | convertPDF |
// +------------+

export async function convertPDF(base64pdf) {

  console.log('converting pdf');
  
  const url = window.serverBase + "pdf";

  const options = {
	method: 'POST',
	body: base64pdf,
	headers: { 'Content-Type': 'text/plain' }
  };

  const response = await fetch(url, options);
  if (response.status !== 200) throw new Error(`convertPDF: ${response.status}`);

  return(await response.text());
}

