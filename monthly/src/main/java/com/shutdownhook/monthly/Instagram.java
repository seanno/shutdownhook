/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.monthly;

import java.io.Closeable;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class Instagram implements Closeable
{
	// +-------+
	// | Setup |
	// +-------+

	public Instagram(WebRequests.Config cfg) throws Exception {
		
		this.requests = new WebRequests(cfg);
		this.gson = new Gson();

		dtfTimestamp = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			.appendOffset("+HHMM", "+0000")
			.toFormatter();
	}
	
	public void close() {
		requests.close();
	}

	// +-------------+
	// | getUserInfo |
	// +-------------+

	public static class UserInfo
	{
		public String id;
		public String username;
	}
	
	public UserInfo getUserInfo(String token) throws Exception {

		WebRequests.Params params = new WebRequests.Params();
		params.addQueryParam("access_token", token);
		params.addQueryParam("fields", "id,username");
		
		String url = Easy.urlPaste(INSTA_GRAPH_BASE, "/me");

		WebRequests.Response response = requests.fetch(url, params);
		if (response.Status == 400) return(null); // bad token
		if (!response.successful()) response.throwException("getUserName");

		return(gson.fromJson(response.Body, UserInfo.class));
	}

	// +---------------------------+
	// | getProcessedPostsForMonth |
	// +---------------------------+

	public static class ProcessedPost
	{
		public String Caption;
		public String TargetUrl;
		public String ImageUrl;
	}
	
	public Map<Integer, List<ProcessedPost>>
		getProcessedPostsForMonth(String token, ZoneId zone,
								  int monthNum, int year) throws Exception {

		
		ZonedDateTime since = ZonedDateTime.of(year, monthNum,
											   1, 0, 0, 0, 0, zone);

		ZonedDateTime until = ZonedDateTime.of(monthNum == 12 ? year + 1 : year,
											   monthNum == 12 ? 1 : monthNum + 1,
											   1, 0, 0, 0, 0, zone);
		
		List<Post> rawPosts = getPosts(token, since.toEpochSecond(), until.toEpochSecond());
		if (rawPosts == null) return(null);

		Map<Integer, List<ProcessedPost>> posts = new HashMap<Integer, List<ProcessedPost>>();
		
		for (Post rawPost : rawPosts) {

			Integer dayOfMonth =
				ZonedDateTime.parse(rawPost.timestamp, dtfTimestamp)
				.withZoneSameInstant(zone)
				.getDayOfMonth();

			// SHAMELESS. I decided to start doing this on January 2, so both Jan 1 and 2
			// pics were posted that day. CHEATING it to make the display work.
			// And again on March 9-10!
			if (rawPost.permalink.endsWith("C1mzm3XxrGP/")) dayOfMonth = 1; 
			if (rawPost.permalink.endsWith("C4U69bMOo-H/")) dayOfMonth = 9; 
			if (rawPost.permalink.endsWith("C7ZK4tpRxeu/")) dayOfMonth = 24; 

			if (!posts.containsKey(dayOfMonth)) {
				posts.put(dayOfMonth, new ArrayList<ProcessedPost>());
			}

			ProcessedPost post = new ProcessedPost();
			post.Caption = rawPost.caption;
			post.TargetUrl = rawPost.permalink;
			
			post.ImageUrl = rawPost.thumbnail_url;
			if (post.ImageUrl == null) post.ImageUrl = rawPost.media_url;

			posts.get(dayOfMonth).add(post);
		}

		return(posts);
	}

	// +----------+
	// | getPosts |
	// +----------+

	public static class Posts
	{
		public Post[] data;
		public Pagination paging;
	}

	public static class Post
	{
		public String timestamp;
		public String caption;
		public String permalink;
		public String media_url;
		public String thumbnail_url;
	}

	public static class Pagination
	{
		public String previous;
		public String next;
	}

	public List<Post> getPosts(String token,
							   Long epochSince,
							   Long epochUntil) throws Exception {

		List<Post> posts = new ArrayList<Post>();

		WebRequests.Params params = new WebRequests.Params();
		params.addQueryParam("access_token", token);
		params.addQueryParam("fields", "timestamp,caption,permalink,media_url,thumbnail_url");

		if (epochSince != null) params.addQueryParam("since", epochSince.toString());
		if (epochUntil != null) params.addQueryParam("until", epochUntil.toString());

		String url = Easy.urlPaste(INSTA_GRAPH_BASE, "/me/media");

		while (url != null) {
			
			WebRequests.Response response = requests.fetch(url, params);
			if (response.Status == 400) return(null); // bad token
			if (!response.successful()) response.throwException("getPosts");

			Posts instaPosts = gson.fromJson(response.Body, Posts.class);
			for (Post post : instaPosts.data) posts.add(post);

			url = (instaPosts.paging == null ? null : instaPosts.paging.next);
			params = new WebRequests.Params();
		}

		return(posts);
	}

	// +---------+
	// | Members |
	// +---------+

	private WebRequests requests;
	private Gson gson;
	DateTimeFormatter dtfTimestamp;

	private final static String INSTA_GRAPH_BASE = "https://graph.instagram.com";
	
	private final static Logger log = Logger.getLogger(Instagram.class.getName());
}
