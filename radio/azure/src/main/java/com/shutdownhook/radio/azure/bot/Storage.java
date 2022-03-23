/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// You know what's dumb? Having to implement an entire provider from scratch
// when an almost perfect one exits but hardcodes the auth mechanism and doesn't
// let you override it. That's dumb.
//
// Also, I spit on your Futures.

package com.shutdownhook.radio.azure.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;

// documented as internal but thrown by readItem, doh
import com.azure.cosmos.implementation.NotFoundException;

import com.shutdownhook.radio.azure.Cosmos;
import com.shutdownhook.toolbox.Easy;

public class Storage extends Cosmos implements com.microsoft.bot.builder.Storage {

	public Storage(Cosmos.Config cfg) throws Exception {
		super(cfg);
	}
	
	// +------+
	// | read |
	// +------+
	
    public CompletableFuture<Map<String, Object>> read(String[] keys) {

		Map<String,Object> results = new HashMap<String,Object>();

		for (String key : keys) {
			try {
				String safeKey = safeKey(key);
				
				BotStorageItem item =
					container.readItem(safeKey, new PartitionKey(safeKey),
									   BotStorageItem.class).getItem();

				results.put(key, objectFromItem(item));
			}
			catch (NotFoundException e) { 
				// just leave it out of the returned map
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "read " + key, false));
			}
		}

		return(CompletableFuture.completedFuture(results));
	}

	// +-------+
	// | write |
	// +-------+
	
	public CompletableFuture<Void> write(Map<String, Object> changes) {
		
		for (String key : changes.keySet()) {
			try {
				container.upsertItem(itemFromObject(key, changes.get(key)));
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "write " + key, false));
			}
		}

		return(CompletableFuture.completedFuture(null));
	}

	// +--------+
	// | delete |
	// +--------+

	public CompletableFuture<Void> delete(String[] keys) {

		for (String key : keys) {
			try {
				String safeKey = safeKey(key);

				container.deleteItem(safeKey, new PartitionKey(safeKey),
									 new CosmosItemRequestOptions());
			}
			catch (Exception e) {
				log.severe(Easy.exMsg(e, "delete " + key, false));
			}
		}

		return(CompletableFuture.completedFuture(null));
	}
	
	// +----------------+
	// | BotStorageItem |
	// +----------------+

	public static class BotStorageItem
	{
		public String id;
		public String ObjectType;
		public String ObjectJson;
	}

	// +-------------------+
	// | Members & Helpers |
	// +-------------------+

	private Object objectFromItem(BotStorageItem item) {
		try {
			return(objectMapper.readValue(item.ObjectJson,
										  Class.forName(item.ObjectType)));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "objectFromItem", false));
			return(null);
		}
	}

	private BotStorageItem itemFromObject(String key, Object o) {
		
		BotStorageItem item = null;

		try {
			item = new BotStorageItem();
			item.id = safeKey(key);
			item.ObjectType = o.getClass().getTypeName();
			item.ObjectJson = objectMapper.writeValueAsString(o);
			return(item);
		}
		catch (JsonProcessingException e) {
			log.severe(Easy.exMsg(e, "itemFromObject", false));
			return(null);
		}
	}
	
	private String safeKey(String key) {
		return(Easy.base64Encode(key)
			   .replaceAll("\\/","-")
			   .replaceAll("\\+","_")
			   .replaceAll("\\=","*"));
	}

	private static final ObjectMapper objectMapper =
		new ObjectMapper()
		.findAndRegisterModules()
		.enableDefaultTyping();

	private final static Logger log =
		Logger.getLogger(Storage.class.getName());
}
