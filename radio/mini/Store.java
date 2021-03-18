
import java.nio.file.*;
import java.util.*;

public class Store
{
	private final static String CHANNEL_SUFFIX = "-channel.json";
	private final static String PLAYLIST_SUFFIX = "-playlist.json";
	
	public Store(String dataDirPath) throws Exception {
		dataDir = Paths.get(dataDirPath);
		if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
		channelLocks = new HashMap<String,Object>();
	}
	
	public Model.Channel getChannel(String channelName) throws Exception {
		String cleanName = getCleanChannelName(channelName);
		synchronized(getChannelLock(cleanName)) {
			Path path = dataDir.resolve(cleanName + CHANNEL_SUFFIX);
			if (!Files.exists(path)) return(null);
			return(Model.Channel.fromJson(new String(Files.readAllBytes(path))));
		}
	}

	public void saveChannel(Model.Channel channel) throws Exception {
		String cleanName = getCleanChannelName(channel.Name);
		synchronized(getChannelLock(cleanName)) {
			Path path = dataDir.resolve(cleanName + CHANNEL_SUFFIX);
			Files.write(path, channel.toJson().getBytes());
		}
	}

	public Model.Playlist getPlaylist(String channelName) throws Exception {
		String cleanName = getCleanChannelName(channelName);
		synchronized(getChannelLock(cleanName)) {
			Path path = dataDir.resolve(cleanName + PLAYLIST_SUFFIX);
			if (!Files.exists(path)) return(null);
			return(Model.Playlist.fromJson(new String(Files.readAllBytes(path))));
		}
	}

	public void savePlaylist(Model.Playlist playlist) throws Exception {
		String cleanName = getCleanChannelName(playlist.ChannelName);
		synchronized(getChannelLock(cleanName)) {
			Path path = dataDir.resolve(cleanName + PLAYLIST_SUFFIX);
			Files.write(path, playlist.toJson().getBytes());
		}
	}

	synchronized private Object getChannelLock(String cleanName) {
		if (!channelLocks.containsKey(cleanName)) channelLocks.put(cleanName, new Object());
		return(channelLocks.get(cleanName));
	}

	private String getCleanChannelName(String inputChannelName) {
		return(inputChannelName.replaceAll("\\W+", "_"));
	}

	private Path dataDir;
	private Map<String,Object> channelLocks;
}

