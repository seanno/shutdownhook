//
//  misc/utility.js
//

import util from 'util';

// +-----------+
// | stringify |
// +-----------+

export function stringify(obj) {
  return(util.inspect(obj, {
	compact: false, // Forces multi-line output
	colors: true    // Adds ANSI color codes for better readability
  }));
}

