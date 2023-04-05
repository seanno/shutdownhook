
const fetch = require('node-fetch');

const COMPLETE_URL = "https://api.openai.com/v1/chat/completions";
const DEFAULT_MODEL = "gpt-3.5-turbo";
const DEFAULT_TEMP = 0.25;

const buildRequest = (prompt) => {
  return({
	"model": DEFAULT_MODEL,
	"temperature": DEFAULT_TEMP,
	"messages": [
	  { "role": "user", "content": prompt }
	]
  });
}

const firstResponse = (response) => {
  return(response.choices.length > 0 ? response.choices[0].message.content : undefined);
}

const complete = async (request) => {

  const token = process.env.OPENAI_API_TOKEN;

  if (!token) throw new Error("missing env OPENAI_API_TOKEN");
  if (!request.messages || !request.messages[0].content) throw new Error("prompt required");
  
  const response = await fetch(COMPLETE_URL, {
	"method": "POST",
	"body": JSON.stringify(request),
	"headers": {
	  "Content-Type": "application/json",
	  "Authorization": "Bearer " + token
	}
  });

  const json = await response.json();
  if (json.error) throw new Error(json.error.message);
  
  return(json);
}

exports.buildRequest = buildRequest;
exports.firstResponse = firstResponse;
exports.complete = complete;

