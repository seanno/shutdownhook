
// +----------+
// | Wrappers |
// +----------+

export async function serverFetchQueries() {
  return(await serverFetch('data/queries'));
}

export async function serverFetchQuery(id) {
  return(await serverFetch('data/query/details?id=' + encodeURIComponent(id)));
}

export async function serverSaveQuery(query) {
  return(await serverFetch('data/query/save', JSON.stringify(query)));
}

export async function serverRunQuery(runQueryInfo) {
  return(await serverFetch('data/query/run', JSON.stringify(runQueryInfo)));
}

export async function serverDeleteQuery(id) {
  return(await serverFetch('data/query/delete?id=' + encodeURIComponent(id)));
}

export async function serverGetSchema(connectionName) {
  return(await serverFetch('data/connection/schema?connection=' +
						   encodeURIComponent(connectionName)));
}

// +--------+
// | Params |
// +--------+

// csv, each item is name:default with default being optional
// no escaping so don't be stupid! :)

export function parseParams(paramsCsv) {

  if (!paramsCsv || paramsCsv.length === 0) return(undefined);
  
  return(paramsCsv.split(",").map((p) => {
	const fields = p.split(":");
	const pobj = { Name: fields[0].trim() };
	if (fields.length > 1) pobj.Default = fields[1].trim();
	return(pobj);
  }));
}

export function paramDefaults(paramsCsv) {
  
  const params = parseParams(paramsCsv);
  if (!params) return(undefined);

  return(params.map((p) => p.Default || ''));
}

// +-------------+
// | serverFetch |
// +-------------+

export async function serverFetch(relativeUrl, body) {

  const url = window.serverBase + relativeUrl;
  
  const options = {
	method: (body ? 'POST' : 'GET'),
	headers: { }
  }

  if (body) {
	options.headers['Content-Type'] = 'application/json';
	options.body = body;
  }
  
  const response = await fetch(url, options);

  if (response.status !== 200) {
	throw new Error(`serverFetch ${url}: ${response.status}`);
  }

  return(await response.json());
}

