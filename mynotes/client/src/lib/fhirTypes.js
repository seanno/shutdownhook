
// +---------------------------+
// | parseDateTimePrecision    |
// | parseDateTime             |
// +---------------------------+

export const PRECISION_NONE = 0;
export const PRECISION_YEAR = 1;
export const PRECISION_MONTH = 2;
export const PRECISION_DAY = 3;
export const PRECISION_TIME = 4;

export function parseDateTimePrecision(d) {

  const dateOnlyRegex = /^\d{4}(-\d{2}(-\d{2})?)?$/;

  let dateParsed;
  let precision;
  
  if (d.match(dateOnlyRegex)) {
	// create in local timezone so displays as expected
	const fields = d.split("-");
	if (fields.length === 1) {
	  dateParsed = new Date(fields[0], 0, 1);
	  precision = PRECISION_YEAR;
	}
	else if (fields.length === 2) {
	  dateParsed = new Date(fields[0], fields[1] - 1, 1);
	  precision = PRECISION_MONTH;
	}
	else {
	  dateParsed = new Date(fields[0], fields[1] - 1, fields[2]);
	  precision = PRECISION_DAY;
	}
  }
  else {
	// fully specified instant (or bogus); spec requires tz
	// so just let the parser figure it out
	dateParsed = new Date(d);
	precision = PRECISION_TIME;
  }

  return([dateParsed, precision]);
}

export function parseDateTime(d) {
  return(parseDateTimePrecision(d)[0]);
}

// +----------------+
// | renderDate     |
// | renderDateTime |
// +----------------+

export function renderDate(d) {

  const [ dateParsed, precision ] = parseDateTimePrecision(d);

  let fmt = {};

  if (precision >= PRECISION_YEAR) fmt.year = 'numeric';
  if (precision >= PRECISION_MONTH) fmt.month = 'numeric';
  if (precision >= PRECISION_DAY) fmt.day = 'numeric';
  
  return(dateParsed.toLocaleString(currentLocale(), fmt));
}

export function renderDateTime(d) {

  const [ dateParsed, precision ] = parseDateTimePrecision(d);

  let fmt = {};

  if (precision >= PRECISION_YEAR) fmt.year = 'numeric';
  if (precision >= PRECISION_MONTH) fmt.month = 'numeric';
  if (precision >= PRECISION_DAY) fmt.day = 'numeric';
  if (precision >= PRECISION_TIME) {
	fmt.hour = 'numeric';
	fmt.minute = 'numeric';
	fmt.timeZoneName = 'short';
  }
  
  return(dateParsed.toLocaleString(currentLocale(), fmt));
}

// +--------------+
// | renderPeriod |
// +--------------+

export function renderPeriod(p) {

  if (p.start && p.end) {

	const startTxt = renderDate(p.start);
	const endTxt = renderDate(p.end);

	return(startTxt === endTxt ? startTxt : startTxt + " to " + endTxt);
  }
  else if (p.start) {
	return("started " + renderDate(p.start));
  }
  else if (p.end) {
	return("ended " + renderDate(p.end));
  }
  
  return("");
}

// +-----------------+
// | renderCodeables |
// | renderCodeable  |
// | renderCodings   |
// | renderCoding    |
// +-----------------+

export function renderCodeables(c) {

  if (!c) return('');
  if (!Array.isArray(c)) return(renderCodeable(c));
  if (c.length === 0) return('');
  
  const texts = [];

  for (var i = 0; i < c.length; ++i) {
	const text = renderCodeable(c[i]);
	if (text && !texts.find((t) => t === text)) texts.push(text);
  }

  return(texts.join('; '));
}

export function renderCodeable(c) {
  if (c.text) return(c.text);
  return(renderCodings(c.coding));
}

export function renderCodings(c) {

  if (!c) return('');
  if (!Array.isArray(c)) return(renderCoding(c));
  if (c.length === 0) return('');
  
  const texts = [];

  for (var i = 0; i < c.length; ++i) {
	const text = renderCoding(c[i]);
	if (text && !texts.find((t) => t === text)) texts.push(text);
  }

  return(texts.join(', '));
}

export function renderCoding(c) {
  return(c.display ? c.display : c.code);
}

// +---------+
// | Helpers |
// +---------+

export function currentLocale() {

  // try to prefer a complete locale vs. just a language
  if (navigator.languages && navigator.languages.length) {
	for (const i in navigator.languages) {
	  const l = navigator.languages[i];
	  if (l.indexOf("-") !== -1) return(l);
	}

	return(navigator.languages[0]);
  }

  return(navigator.language ? navigator.language : "en-US");
}

