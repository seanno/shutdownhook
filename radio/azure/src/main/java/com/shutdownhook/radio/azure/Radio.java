/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure;

import java.time.Instant;
import java.util.*;
import java.io.*;
import java.util.logging.Logger;

public class Radio
{
	private final static Logger log = Logger.getLogger(Radio.class.getName());

	private final static String DEFAULT_HTML = "player";
	
	public Radio(Store store) {
		this.store = store;
	}

	public Model.Channel getChannel(String channelName) throws Exception {

		Model.Channel channel = store.getChannel(channelName);

		if (channel == null) {
			channel = new Model.Channel();
			channel.Name = channelName;
		}

		return(updateIfNeeded(channel));
	}

	private Model.Channel updateIfNeeded(Model.Channel channel) throws Exception {

		if (channel.CurrentVideo != null) {

			int duration = channel.CurrentVideo.DurationSeconds;
			duration -= 2; // slop because machines are fast
			if (duration < 1) duration = 1;
			
			Instant doneAt = channel.CurrentVideoStarted.plusSeconds(duration);
			Instant now = Instant.now();
			log.info(String.format("updateIfNeeded; doneAt = %s; now = %s", doneAt, now));

			if (now.isBefore(doneAt)) {
				// all good, keep going with this one
				return(channel);
			}
		}

		// note there is plenty of room here for simultaneous calls to all
		// try to update the current video. On the assumption that volume on
		// a particular channel will be relatively limited, we're just going to
		// live with that, make sure we keep the data consistnt and allow the
		// last write to win. That's why we re-read the channel from the store
		// when we're done here, just in case we lost it'll tend to keep us all
		// in sync still.
		
		Model.Playlist playlist = getPlaylist(channel.Name);

		// get this out of the way quickly, less to special-case later
		if (playlist.Videos == null || playlist.Videos.size() == 0) {
			channel.CurrentVideo = null;
			channel.CurrentVideoStarted = null;
			return(channel);
		}

		// walk backwards looking for the earliest unplayed video
		// videos are always added to the end so we just need to find
		// the break point. We walk from the end of the line because
		// over time that's likely to be a lot more efficient.

		int i = -1;
		for (i = playlist.Videos.size() - 1; i >= 0; --i) {
			if (playlist.Videos.get(i).Played) break;
		}

		i = i + 1;
		if (i < playlist.Videos.size()) {
			// found one!
			playlist.Videos.get(i).Played = true;
			store.savePlaylist(playlist);
			log.info(String.format("updateIfNeeded; found unplayed vid %d %s", i, playlist.Videos.get(i).Id));
		}
		else {
			// all played, so shuffle a random one (but not the current one)
			boolean keepLooking = true;
			String currentId = (channel.CurrentVideo == null ? "" : channel.CurrentVideo.Id);
			while (keepLooking) {
				i = new Random().nextInt(playlist.Videos.size());
				if (playlist.Videos.size() == 1 || !playlist.Videos.get(i).Id.equals(currentId)) keepLooking = false;
			}
			
			log.info(String.format("updateIfNeeded; picked random vid %d %s", i, playlist.Videos.get(i).Id));
		}
		
		channel.CurrentVideo = playlist.Videos.get(i);
		channel.CurrentVideoStarted = Instant.now();
		store.saveChannel(channel);
		
		return(store.getChannel(channel.Name));
	}
	
	public Model.Playlist getPlaylist(String channelName) throws Exception {

		Model.Playlist playlist = store.getPlaylist(channelName);

		if (playlist == null) {
			playlist = new Model.Playlist();
			playlist.ChannelName = channelName;
		}

		return(playlist);
	}

	public List<Model.Video> searchVideos(String query, int maxResults) throws Exception {

		List<Model.Video> videos = new ArrayList<Model.Video>();
		
		List<YouTube.VideoInfo> infos = YouTube.search(query, maxResults);
		for (YouTube.VideoInfo info : infos) {

			Model.Video video = new Model.Video();
			videos.add(video);
			
			video.Id = info.Id;
			video.Title = info.Title;
			video.ThumbnailUrl = info.ThumbnailUrl;
			video.DurationSeconds = info.DurationSeconds;
		}

		return(videos);
	}

	public Model.Video addVideo(String channelName, String videoUrlOrId,
								String who) throws Exception {

		YouTube.VideoInfo info = YouTube.getVideoInfo(videoUrlOrId);

		Model.Playlist playlist = getPlaylist(channelName);
		if (playlist.Videos == null) playlist.Videos = new ArrayList<Model.Video>();

		Model.Video video = new Model.Video();
		playlist.Videos.add(video);

		video.Added = Instant.now();
		video.AddedBy = who;
		video.Played = false;
		video.Id = info.Id;
		video.Title = info.Title;
		video.ThumbnailUrl = info.ThumbnailUrl;
		video.DurationSeconds = info.DurationSeconds;

		store.savePlaylist(playlist);

		return(video);
	}

	public String getStaticHtml(String name) throws Exception {
		
		InputStream stream = null;
		InputStreamReader reader = null;
		BufferedReader buffered = null;

		try {
			String inputName = ((name == null || name.isEmpty()) ? DEFAULT_HTML : name);
			stream = getClass().getClassLoader().getResourceAsStream(inputName + ".html");
			if (stream == null) return(null);

			reader = new InputStreamReader(stream);
			buffered = new BufferedReader(reader);

			StringBuilder sb = new StringBuilder();
			String line = null;

			while ((line = buffered.readLine()) != null) {
				sb.append(line).append("\n");
			}

			return(sb.toString());
		}
		finally {
			if (buffered != null) buffered.close();
			if (reader != null) reader.close();
			if (stream != null) stream.close();
		}
	}

	private Store store;
}
