//
// Approach taken from remoku.tv, Copyright 2012 A. Cassidy Napoli, New BSD License
// This code carries those same obligations.
//
// The Roku ECP interface at port 8060 doesn't set CORS headers, so normal ajax
// calls don't work. Posting to a hidden form avoids the error. This is only cool
// because we don't need the returned content (it also means we can't use apis where
// that's not the case, like /query/apps).
//
// Read more at https://shutdownhook.com
//

// +-------+
// | Calls |
// +-------+

function rokuLaunch(addr, app_id) {
	rokuCall(addr, '/launch/' + app_id);
}

function rokuSearch(addr, keyword) {
	rokuSearch(addr, keyword, '');
}

function rokuSearch(addr, keyword, app_id) {
	var url = '/search/browse?title=' + escape(keyword);
	if (app_id) url = url + '&provider-id=' + escape(app_id) + '&launch=true';
	rokuCall(addr, url);
}

function rokuCall(addr, url) {
	rokuEnsureSetup();
	rokuForm.setAttribute('action', 'http://' + addr + ':8060' + url);
	rokuForm.submit();
}

// +------+
// | Apps |
// +------+

// Sure would be nice if Roku fixed their CORS issue so that this list
// was updated and complete. Oh well, we'll just cherry-pick the most common.
// Feel free to let me know if there's one you want.

const rokuAppList = [
	{ id: '14295',  name: 'Acorn' },
	{ id: '551012', name: 'Apple TV' },
	{ id: '143088', name: 'BritBox' },
	{ id: '291097', name: 'Disney Plus' },
	{ id: '61322',  name: 'HBO Max' },
	{ id: '2285',   name: 'Hulu' },
	{ id: '12',     name: 'Netflix' },
	{ id: '593099', name: 'Peacock' },
	{ id: '13',     name: 'Prime Video' },
	{ id: '41468',  name: 'Tubi' },
	{ id: '837',    name: 'YouTube' },
	{ id: '195316', name: 'YouTube TV' }
];

function rokuApps() {
	return(rokuAppList);
}

function rokuLookupAppId(name) {

	var nameLower = name.trim().toLowerCase();

	var appid = '';

	rokuAppList.forEach(function(item) {

		var appLower = item.name.toLowerCase();

		if (nameLower.indexOf(appLower) != -1 ||
			appLower.indexOf(nameLower) != -1) {

			appid = item.id;
			return;
		}
	});

	return(appid);
}

// +---------+
// | Private |
// +---------+

var rokuForm;

function rokuEnsureSetup() {

	if (rokuForm) return;

	var iframe = document.createElement('iframe');
	iframe.name = 'rokusink';
	iframe.id = 'rokusink';
	iframe.style.visibility = 'hidden';
	iframe.style.display = 'none';
	document.body.appendChild(iframe);

	rokuForm = document.createElement('form');
	rokuForm.id = 'rokuform';
	rokuForm.style.visibility = 'hidden';
	rokuForm.style.display = 'none';
	rokuForm.method = 'POST';
	rokuForm.target = 'rokusink';
	document.body.appendChild(rokuForm);
}
