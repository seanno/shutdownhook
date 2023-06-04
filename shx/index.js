//
// Read about this code at http://shutdownhook.com
// MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
//

const process = require('process');
const jose = require('node-jose');
const fs = require('fs');
const zlib = require('zlib');
const qrcode = require('qrcode');

// +---------+
// | newKeys |
// +---------+

const newKeys = async () => {

  const keystore = jose.JWK.createKeyStore();
  const props = { "use": "sig", "alg": "ES256" };
  const key = await keystore.generate("EC", "P-256", props);

  const publicJSON = { "keys": [ key.toJSON() ] };
  console.log(">>> PUBLIC\n" + JSON.stringify(publicJSON, null, 2));

  const privateJSON = { "keys": [ key.toJSON(true) ] };
  console.log(">>> PRIVATE\n" + JSON.stringify(privateJSON, null, 2));
}

// +----------+
// | signCard |
// +----------+

const signCard = async (cardPath, keyStorePath) => {

  const cardJSON = JSON.parse(fs.readFileSync(cardPath));
  const compressedCard = zlib.deflateRawSync(JSON.stringify(cardJSON));

  const keystoreJSON = JSON.parse(fs.readFileSync(keyStorePath));
  const keystore = await jose.JWK.asKeyStore(keystoreJSON);
  const signingKey = keystore.all()[0];

  const options = { format: 'compact', fields: { zip: 'DEF' } };
  const signer = jose.JWS.createSign(options, signingKey);
  const jws = await signer.update(Buffer.from(compressedCard)).final();

  console.log(jws);
}

// +--------+
// | MakeQR |
// +--------+

const makeQR = async (jws, path) => {
  
  const numericJWS = jws.split('')
        .map((c) => c.charCodeAt(0) - 45)
        .flatMap((c) => [Math.floor(c / 10), c % 10]) // Need to maintain leading zeros
        .join('');

  const segments = [
	{ data: 'shc:/', mode: 'byte' },
	{ data: numericJWS, mode: 'numeric' }
  ];

  await qrcode.toFile(path, segments, {
	width: 600,
	errorCorrectionLevel: 'L'
  });
}

// +------------+
// | entrypoint |
// +------------+

const usage = () => {
  console.log("USAGE:");
  console.log("  node . newkeys");
  console.log("  node . signCard JSON_PATH KEYSTORE_PATH");
  console.log("  node . makeQR JWS QR_PATH");
}

switch (process.argv[2]) {
  case "newkeys": newKeys(); break;
  case "signCard": signCard(process.argv[3], process.argv[4]); break;
  case "makeQR": makeQR(process.argv[3], process.argv[4]); break;
  default: usage(); break;
}

