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
import com.azure.identity.DefaultAzureCredentialBuilder;

public class Cosmos implements Closeable
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

	public Cosmos(Config cfg) throws Exception {

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

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	protected Config cfg;
	protected CosmosClient client;
	protected CosmosDatabase database;
	protected CosmosContainer container;

	private final static Logger log = Logger.getLogger(Cosmos.class.getName());
}

