
const { BskyAgent, RichText } = require('@atproto/api');

const skeet = async (msg, imageUrl, imageAlt) => {

  const service = process.env.BLUESKY_SERVICE;
  const login = process.env.BLUESKY_LOGIN;
  const pass = process.env.BLUESKY_PASSWORD;

  if (!service) throw new Error("missing env BLUESKY_SERVICE");
  if (!login) throw new Error("missing env BLUESKY_LOGIN");
  if (!pass) throw new Error("missing env BLUESKY_PASSWORD");

  // login
  const agent = new BskyAgent({
	service: service
  });

  await agent.login({
	identifier: login,
	password: pass
  });

  // rich text
  const rt = new RichText({
	text: msg
  });

  await rt.detectFacets(agent);

  const post = {
	text: rt.text,
	facets: rt.facets
  };
  
  // image
  if (imageUrl) {

	const imgResponse = await fetch(imageUrl);
	const imgBlob = await imgResponse.blob();
	const imgData = await agent.uploadBlob(imgBlob);

	post.embed = {
	  $type: 'app.bsky.embed.images',
	  images: [ {
		alt: imageAlt,
		image: imgData.data.blob
	  }]
	};
  }
  
  // post
  return(await agent.post(post));
}

exports.skeet = skeet;

