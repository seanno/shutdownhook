
import { parseDateTime, renderCodeable, renderCodeables, renderDateTime, renderPeriod } from './fhirTypes.js';

// +--------------------+
// | getResourceHandler |
// +--------------------+

export function getResourceHandler(resourceType) {

  switch (resourceType) {
	case 'Encounter': return(encounterHandler());
	case 'DocumentReference': return(documentHandler());
	default: throw new Error('no handler for resource type ' + resourceType);
  }
}

// +------------------+
// | encounterHandler |
// +------------------+

function encounterHandler() {
  
  return({

	// +--------
	// | public
	
	primaryText: function(r) {
	  return(this.getType(r) ?? 'Visit');
	},

	secondaryTexts: function(r) {
	  
	  const texts = [];
	  
	  if (r.period) texts.push(renderPeriod(r.period));

	  const loc = this.getLocation(r);
	  if (loc) texts.push(loc);

	  return(texts);
	},

	compare: function(a, b) {

	  const aActive = this.isActive(a);
	  const bActive = this.isActive(b);

	  // active floats to the top
	  if (aActive && !bActive) return(-1);
	  if (!aActive && bActive) return(1);

	  const aDate = this.getParsedDate(a);
	  const bDate = this.getParsedDate(b);
	  
	  // no date sinks to the bottom
	  if (aDate && !bDate) return(-1);
	  if (!aDate && bDate) return(1);

	  // else compare by date
	  if (!aDate && !bDate) return(compareIds(a, b));
	  const cmp = compareDates(bDate, aDate); // reverse
	  return(cmp === 0 ? compareIds(a, b) : cmp);
	},

	// +---------
	// | private

	isActive: function(r) {
	  return(r.status === 'active' ||
			 r.status === 'triaged' ||
			 r.status === 'in-progress' ||
			 r.status === 'onleave');
	},

	getParsedDate: function(r) {
	  return(r.period && r.period.start ? parseDateTime(r.period.start)
			 : (r.period && r.period.end ? parseDateTime(r.period.end)
				: null));
	},

	getLocation: function(r) {
	  
	  const loc = r.location;
	  
	  if (!loc) return(null);
	  if (!Array.isArray(loc)) return(loc.display ?? null);
	  if (loc.length == 0) return(null);

	  const texts = [];
	  for (var i = 0; i < loc.length; ++i) {
		const thisLoc = loc[i].location;
		const text = (thisLoc ? thisLoc.display : null);
		if (text && !texts.find((t) => t === text)) texts.push(text);
	  }

	  return(texts.join("; "));
	},

	getType: function(r) {

	  if (r.serviceType) return(renderCodeables(r.serviceType));
	  if (r.type) return(renderCodeables(r.type));
	  return(null);
	}
	
	
  });
}

// +-----------------+
// | documentHandler |
// +-----------------+

function documentHandler() {
  
  return({

	// +--------
	// | public
	
	primaryText: function(r) {

	  var text = (r.description ? r.description
				  : (r.category && r.category.length < 0 ? renderCodeables(r.category)
					 : (r.type ? renderCodeable(r.type)
						: 'Document')));

	  return(text);
	},

	secondaryTexts: function(r) {

	  var texts = [];

	  if (r.author && r.author.length > 0 && r.author[0].display) {
		texts.push(r.author[0].display);
	  }
	  
	  if (r.date) texts.push(renderDateTime(r.date));
	  else if (r.period && r.period.end) texts.push(renderDateTime(r.period.end));
	  else if (r.period && r.period.start) texts.push(renderDateTime(r.period.start));
	  
	  return(texts);
	},

	compare: function(a, b) {

	  const aDate = (a.date ? parseDateTime(a.date) : null);
	  const bDate = (b.date ? parseDateTime(b.date) : null);

	  if (aDate && !bDate) return(-1);
	  if (!aDate && bDate) return(1);

	  if (!aDate && !bDate) return(compareIds(a, b));

	  const cmp = compareDates(bDate, aDate); // reverse
	  return(cmp === 0 ? compareIds(a, b) : cmp);
	}

	// +---------
	// | private
	
  });
}

// +---------+
// | helpers |
// +---------+

function compareIds(a, b) {
  return(a.id.localeCompare(b.id));
}

function compareDates(a, b) {
  return(a.getTime() - b.getTime());
}
