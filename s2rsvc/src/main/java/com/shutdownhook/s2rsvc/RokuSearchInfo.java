/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.s2rsvc;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import com.shutdownhook.toolbox.Easy;

public class RokuSearchInfo
{
	public static class ChannelTarget
	{
		public String ChannelId;
		public String ContentId;
		public String MediaType;

		@Override
		public String toString() {
			return(String.format("%s:%s:%s", ChannelId, ContentId, MediaType));
		}
	}
	
	public String Search;
	public String Season;
	public String Number;
	public List<ChannelTarget> Channels = new ArrayList<ChannelTarget>();

	public void addChannelTarget(String channelId, String contentId, String mediaType,
								 UserChannelSet userChannels) {

		if (userChannels != null && !userChannels.ok(channelId)) return;

		for (ChannelTarget current : Channels) {
			
			if (current.ChannelId.equals(channelId)) {
				
				if (current.ContentId == null) {
					current.ContentId = contentId;
					current.MediaType = mediaType;
					return;
				}
			}
		}
			
		ChannelTarget target = new ChannelTarget();
		target.ChannelId = channelId;
		target.ContentId = contentId;
		target.MediaType = mediaType;
		Channels.add(target);
	}
	
	public RokuSearchInfoV1 toV1() {
		
		RokuSearchInfoV1 v1 = new RokuSearchInfoV1();
		
		v1.Search = this.Search;
		v1.Season = this.Season;
		v1.Number = this.Number;
		
		if (this.Channels.size() > 0) {
			
			v1.Channel = this.Channels.get(0).ChannelId;
			v1.ContentId = this.Channels.get(0).ContentId;
			v1.MediaType = this.Channels.get(0).MediaType;
		}

		return(v1);
	}

	@Override
	public String toString() {
		return(String.format("%s|%s|%s|%s", Search, Season, Number,
				 (Channels == null ? null : Easy.join(Channels, ","))));
	}

	public interface Parser extends Closeable {
		// return an info if you were able to parse, otherwise null
		public RokuSearchInfo parse(String input, UserChannelSet channels) throws Exception;
	}

	public interface Refiner extends Closeable {
		// do your best to improve things in place
		public void refine(RokuSearchInfo info, UserChannelSet channels) throws Exception;
	}
}

