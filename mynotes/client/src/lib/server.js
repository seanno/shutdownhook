
// +----------+
// | wrappers |
// +----------+

export async function convertPDF(base64data) {
  return(await serverFetch("pdf", base64data));
}

export async function explain(input) {
  return(await serverFetch("explain", input));
}

// +-------------+
// | serverFetch |
// +-------------+

export async function serverFetch(endpoint, input) {

  const url = window.serverBase + endpoint;

  const options = {
	method: 'POST',
	body: input,
	headers: { 'Content-Type': 'text/plain' }
  };

  const response = await fetch(url, options);
  if (response.status !== 200) throw new Error(`serverFetch (${endpoint}): ${response.status}`);

  return(await response.text());
}

