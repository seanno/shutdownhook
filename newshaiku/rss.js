
const fetch = require('node-fetch');
const xml2js = require('xml2js');

const getItemTitlesAndLinks = async (url) => {
  
  const response = await fetch(url);
  const xml = await response.text();

  const titles = [];
  const links = [];
  const images = [];

  xml2js.parseString(xml, (err, json) => {

	for (const i in json.rss.channel[0].item) {

	  const item = json.rss.channel[0].item[i];

	  titles.push(item.title[0]);
	  links.push(item.link[0]);

	  try {
		images.push(item['media:content'][0]['$']['url']);
	  }
	  catch (err) {
		images.push(undefined);
	  }
	}
  });

  return([titles, links, images]);
}

exports.getItemTitlesAndLinks = getItemTitlesAndLinks;


