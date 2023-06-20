const fs = require('fs');

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

const manifestId = process.argv[2];

const params = {
  recipient: "demo",
  passcode: "fancy-passcode",
  embeddedLengthMax: 0 // always use 'location'
}

const go = async () => {

  const response = await fetch("https://localhost:7071/manifest/" + manifestId, {
	method: "POST",
	headers: { "Content-Type": "application/json" }, // note no admin token
	body: JSON.stringify(params)
  });

  if (response.status !== 200) {
	console.error(`Failed ${response.status} (${response.statusText})`);
	return;
  }
  
  const json = await response.json();
  console.log(JSON.stringify(json, null, 2));
}

go();


