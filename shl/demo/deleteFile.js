const fs = require('fs');

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

const adminToken = "demotime";

const params = {
  ManifestId: process.argv[2],
  ManifestUniqueName: process.argv[3]
}
	  
const go = async () => {

  const response = await fetch("https://localhost:7071/deleteFile", {
	method: "POST",
	headers: { "Content-Type": "application/json", "X-SHL-AdminToken": adminToken },
	body: JSON.stringify(params)
  });

  if (response.status !== 200) {
	console.error(`Failed ${response.status} (${response.statusText})`);
	return;
  }
  
  const body = await response.text();
  console.log(body);
}

go();


