
const { TwitterApi } = require('twitter-api-v2');

const tweet = async (msg) => {

  const cfg = {
	"appKey": process.env.TWITTER_API_APP_KEY,
	"appSecret": process.env.TWITTER_API_APP_SECRET,
	"accessToken": process.env.TWITTER_API_ACCESS_TOKEN_KEY,
	"accessSecret": process.env.TWITTER_API_ACCESS_TOKEN_SECRET
  };

  if (!cfg.appKey) throw new Error("missing env TWITTER_API_APP_KEY");
  if (!cfg.appSecret) throw new Error("missing env TWITTER_API_APP_SECRET");
  if (!cfg.accessToken) throw new Error("missing env TWITTER_API_ACCESS_TOKEN_KEY");
  if (!cfg.accessSecret) throw new Error("missing env TWITTER_API_ACCESS_TOKEN_SECRET");

  const userClient = new TwitterApi(cfg);
  const tweet = await userClient.v2.tweet(msg);

  return(tweet);
}

exports.tweet = tweet;

