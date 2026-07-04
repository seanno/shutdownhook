//
// UTILITY.JAVA
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.time.Instant;
import java.lang.reflect.Type;
import java.util.logging.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.WebRequests;

public class Utility implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public WebRequests.Config Requests = new WebRequests.Config();
		public Integer ExecThreads = Exec.CACHED_THREADPOOL;
	}

	public Utility(Config cfg) throws Exception {
		this.cfg = cfg;
		this.gson = createGson(true);
		this.gsonCompact = createGson(false);
		this.requests = new WebRequests(cfg.Requests);
		this.exec = new Exec(cfg.ExecThreads);
	}

	public void close() {
		requests.close();
		exec.close();
	}

	// +-----------+
	// | Utilities |
	// +-----------+

	public Gson getGson() { return(gson); }
	public Gson getCompactGson() { return(gsonCompact); }
	public WebRequests getRequests() { return(requests); }
	public Exec getExec() { return(exec); }

	// +------------------+
	// | escapeJsonString |
	// +------------------+

	public static String escapeJsonString(String input) {
		String json = new JsonPrimitive(input).toString();
		return(json.substring(1, json.length() - 1));
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static Gson createGson(boolean pretty) {

		GsonBuilder builder = new GsonBuilder()
			.registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
				public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext ctx) {
					return(new JsonPrimitive(src.toString()));
				}
			})
			.registerTypeAdapter(Instant.class, new JsonDeserializer<Instant>() {
				public Instant deserialize(JsonElement json, Type typeOfT,
										   JsonDeserializationContext ctx) throws JsonParseException {
					return(Instant.parse(json.getAsString()));
				}
			});

		if (pretty) builder.setPrettyPrinting();

		return(builder.create());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Gson gson;
	private Gson gsonCompact;
	private WebRequests requests;
	private Exec exec;
	
	private final static Logger log = Logger.getLogger(Utility.class.getName());
}
