
const fs = require('fs');

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

const adminToken = "demotime";

const createParams = {

  Passcode: "fancy-passcode",  // require a passcode; "P" flag in payload
  TtlSeconds: 60 * 60 * 24,    // expire SHL after 24 hours
  RetrySeconds: 0,             // no "L" flag in payload
  EncryptFiles: true,          // files are not pre-encrypted; allocate a key
  Label: "Fancy Label",

  Files: [
	{
	  ManifestUniqueName: "ips",
	  ContentType: "application/fhir+json",
	  FileB64u: fs.readFileSync("files/ips.b64u.txt").toString()
	},
	{
	  ManifestUniqueName: "shc",
	  ContentType: "application/smart-health-card",
	  FileB64u: fs.readFileSync("files/shc.b64u.txt").toString()
	}
  ]
}

const go = async () => {

  const response = await fetch("https://localhost:7071/createPayload", {
	method: "POST",
	headers: { "Content-Type": "application/json", "X-SHL-AdminToken": adminToken },
	body: JSON.stringify(createParams)
  });

  if (response.status !== 200) {
	console.error(`Failed ${response.status} (${response.statusText})`);
	return;
  }
  
  const json = await response.json();
  const jsonStr = JSON.stringify(json, null, 2);
  console.log(jsonStr);

  if (createParams.EncryptFiles) {
	console.log("");
	console.log("shlink:/" + Buffer.from(jsonStr).toString("base64url"));
  }
}

go();


