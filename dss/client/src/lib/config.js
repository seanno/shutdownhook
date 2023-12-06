

const _cfg = {
  "serverBase": "https://spndev.mshome.net:3001"
}

export default function config(key) {
  return(_cfg[key]);
}

