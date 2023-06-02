const process = require('process');
const jose = require('node-jose');

const makeKeys = async () => {

  const keystore = jose.JWK.createKeyStore();
  const props = { "use": "sig", "alg": "ES256" };
  const key = await keystore.generate("EC", "P-256", props);

  const publicJSON = { "keys": [ key.toJSON() ] };
  console.log(">>> PUBLIC\n" + JSON.stringify(publicJSON, null, 2));

  const privateJSON = { "keys": [ key.toJSON(true) ] };
  console.log(">>> PRIVATE\n" + JSON.stringify(privateJSON, null, 2));
}

switch (process.argv[2]) {
  case "newkey": makeKeys(); break;
  default: console.log("USAGE: node . [newkey]");
}

