
// +------------+
// | b64uEncode |
// +------------+

export function b64uEncode(inputString) {

  // turn the string into b64
  const b64 = btoa(inputString);

  // and turn that into b64u
  return(b64
		 .replace(/\+/g, '-')
		 .replace(/\//g, '_')
		 .replace(/=/g, ''));
}

// +------------+
// | b64uDecode |
// +------------+

export function b64uDecode(inputB64url) {

  // turn the string from b64u to b64
  let b64 = inputB64url
	  .replace(/-/g, '+')
	  .replace(/_/g, '/');

  const pad = b64.length % 4;
  if (pad) b64 += new Array(5 - pad).join('=');

  // turn it into a decoded Uint8Array
  const raw = atob(b64);
  const arr = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; ++i) arr[i] = raw.charCodeAt(i);

  // and turn that into a string
  return(new TextDecoder().decode(arr));
}

// +-----------+
// | toCSV     |
// | toCSVLine |
// +-----------+

export function toCSVLine(inputArray) {

  const escapedArray = inputArray.map((field) => {
	
	let f = field;
	if (!f) return('');
	
	const ichComma = f.indexOf(",");
	const ichNewline = f.indexOf("\n");

	if (ichComma === -1 && ichNewline === -1) return(f);
	
	const ichQuote = f.indexOf("\"");
	if (ichQuote !== -1) f = f.replace(/"/g, '""');
	return('"' + f + '"');
  });

  return(escapedArray.join(','));
}

export function toCSV(inputArrayOfArrays) {
  return(inputArrayOfArrays.map((a) => toCSVLine(a)).join('\n'));
}

// +------------+
// | saveToFile |
// +------------+

export function saveToFile(filename, contents, mimeType) {

  const blob = new Blob([contents], { type: mimeType });
  const url = window.URL.createObjectURL(blob);
  
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
