
const RSS_URL = "https://rss.upi.com/news/top_news.rss";
const PROMPT_ASK = "write a funny haiku summarizing this topic: ";

const dotenv = require('dotenv');
const rss = require('./rss.js');
const openai = require('./openai.js');
const twitter = require('./twitter.js');

const go = async () => {

  try {
	// get news items
	const [ titles, links ] = await rss.getItemTitlesAndLinks(RSS_URL);
	const i = Math.floor(Math.random() * titles.length);

	// get haiku
	const prompt = PROMPT_ASK + titles[i];
	const completion = await openai.complete(openai.buildRequest(prompt));
	const haiku = openai.firstResponse(completion);

	// tweet away
	const tweet = await twitter.tweet(haiku + "\n" + links[i]);
	console.log(tweet);
  }
  catch (err) {
	console.error(err);
  }
}

dotenv.config();
go();

