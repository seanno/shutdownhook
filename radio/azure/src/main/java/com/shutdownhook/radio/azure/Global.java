/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.radio.azure;

import java.util.logging.Logger;

// internal but necessary to serialize/deserialize Instants
import com.azure.cosmos.implementation.Utils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class Global
{
	public synchronized static Radio getRadio() throws Exception {
		
		if (radio == null) {

			Store.Config cfg = new Store.Config();
			cfg.Endpoint = System.getenv("COSMOS_ENDPOINT");
			cfg.Database = System.getenv("COSMOS_DATABASE");
			cfg.Container = System.getenv("COSMOS_CONTAINER");

			log.info(String.format("Initializing Cosmos connection: %s / %s / %s",
								   cfg.Endpoint, cfg.Database, cfg.Container));
			
			store = new Store(cfg);
			radio = new Radio(store);

			log.info("Adding JavaTimeModule to Cosmos Utils ObjectMapper");
			Utils.getSimpleObjectMapper().registerModule(new JavaTimeModule());
		}

		return(radio);
	}

	public static boolean booleanConfig(String name, boolean defaultValue) {
		
		boolean val = defaultValue;

		String env = System.getenv(name);
		if (env == null || env.trim().isEmpty()) return(val);
		
		try { val = Boolean.parseBoolean(env); }
		catch (Exception e) { /* eat it */ }

		return(val);
	}

	private static Radio radio = null;
	private static Store store = null;
	
	private final static Logger log = Logger.getLogger(Global.class.getName());
}
