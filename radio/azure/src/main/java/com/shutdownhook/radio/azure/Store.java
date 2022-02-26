/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure;

import java.io.Closeable;
import java.util.logging.Logger;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.identity.DefaultAzureCredentialBuilder;

// documented as internal but thrown by readItem, doh
import com.azure.cosmos.implementation.NotFoundException;

public class Store
{
	// +-------+
	// | Setup |
	// +-------+

	public static class Config
	{
		public String Endpoint;
		public String Database;
		public String Container;
	}

	public Store(Config cfg) throws Exception {

		this.cfg = cfg;
		
		this.client = new CosmosClientBuilder()
			.endpoint(cfg.Endpoint)
			.credential(new DefaultAzureCredentialBuilder().build())
			.buildClient();

		this.database = client.getDatabase(cfg.Database);
		this.container = database.getContainer(cfg.Container);
	}

	public void close() {
		if (client != null) client.close();
	}
	
	public Model.Channel getChannel(String channelName) throws Exception {

		Model.Channel c = null;
		String id = getCosmosId(channelName, CHANNEL_SUFFIX);

		try {
			c = container.readItem(id, new PartitionKey(id), Model.Channel.class).getItem();
		}
		catch (NotFoundException e) { 
			// null return == not found
		}

		return(c);
	}

	public void saveChannel(Model.Channel channel) throws Exception {
		channel.id = getCosmosId(channel.Name, CHANNEL_SUFFIX);
		container.upsertItem(channel);
	}

	public Model.Playlist getPlaylist(String channelName) throws Exception {

		Model.Playlist p = null;
		String id = getCosmosId(channelName, PLAYLIST_SUFFIX);
		
		try {
			p = container.readItem(id, new PartitionKey(id), Model.Playlist.class).getItem();
		}
		catch (NotFoundException e) { 
			// null return == not found
		}

		return(p);
	}

	public void savePlaylist(Model.Playlist playlist) throws Exception {
		playlist.id = getCosmosId(playlist.ChannelName, PLAYLIST_SUFFIX);
		container.upsertItem(playlist);
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private String getCosmosId(String inputChannelName, String suffix) {
		return(inputChannelName + suffix);
	}

	private final static String CHANNEL_SUFFIX = "_ch";
	private final static String PLAYLIST_SUFFIX = "_pl";
	
	private Config cfg;
	private CosmosClient client;
	private CosmosDatabase database;
	private CosmosContainer container;

	private final static Logger log = Logger.getLogger(Store.class.getName());
}

