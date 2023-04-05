
const fetch = require('node-fetch');
const xml2js = require('xml2js');

const getItemTitlesAndLinks = async (url) => {
  
  const response = await fetch(url);
  const xml = await response.text();

  const titles = [];
  const links = [];

  xml2js.parseString(xml, (err, json) => {

	for (const i in json.rss.channel[0].item) {

	  const item = json.rss.channel[0].item[i];
	  
	  titles.push(item.title);
	  links.push(item.link);
	}
  });

  return([titles, links]);
}

exports.getItemTitlesAndLinks = getItemTitlesAndLinks;


