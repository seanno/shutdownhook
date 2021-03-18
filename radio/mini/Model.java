
import java.time.Instant;
import java.util.List;

import com.google.gson.Gson;

public class Model
{
	public static class Channel 
	{
		public String Name;
		public Video CurrentVideo;
		public Instant CurrentVideoStarted;

		public String toJson() {
			return(new Gson().toJson(this));
		}
		
		public static Channel fromJson(String json) throws Exception {
			return(new Gson().fromJson(json, Channel.class));
		}
	}
	
	public static class Playlist 
	{
		public String ChannelName;
		public List<Video> Videos;

		public String toJson() {
			return(new Gson().toJson(this));
		}

		public static Playlist fromJson(String json) throws Exception {
			return(new Gson().fromJson(json, Playlist.class));
		}
	}

	public static class Video 
	{
		public String Id;
		public String Title;
		public Instant Added;
		public String AddedBy;
		public boolean Played;
		public int DurationSeconds;
	}
}
