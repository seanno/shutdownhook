
import config from './config.js';

// +----------+
// | Wrappers |
// +----------+

export async function serverFetchQueries() {
  return(await serverFetch('/queries'));
}

export async function serverFetchQuery(id) {
  return(await serverFetch('/query/details?id=' + encodeURIComponent(id)));
}

export async function serverSaveQuery(query) {
  return(await serverFetch('/query/save', JSON.stringify(query)));
}

export async function serverRunQuery(runQueryInfo) {
  return(await serverFetch('/query/run', JSON.stringify(runQueryInfo)));
}

export async function serverDeleteQuery(id) {
  return(await serverFetch('/query/delete?id=' + encodeURIComponent(id)));
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

  const url = config('serverBase') + relativeUrl;
  
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

