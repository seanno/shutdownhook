//
// misc/index.js
//
// process.env.Mode 
//

import { checkBellevueZWay } from './zway.js';
import { checkFlair } from './flair.js';

// +------------+
// | Entrypoint |
// +------------+

try {

  const mode = (process.argv.length >= 3 ? process.argv[2] : process.env.Mode);
  
  switch (mode) {
	  
	case "zway":
	  await checkBellevueZWay();
	  break;

	case "flair":
	  await checkFlair();
	  break;

	default:
	  await checkBellevueZWay();
	  await checkFlair();
	  break;
  }
}
finally {
  process.exit(0);
}


