//
// TOOLCALLING.JAVA
//

package com.shutdownhook.colossus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.WebRequests;

public class ToolCalling
{
	public static class ToolClass
	{
		public String ClassName; // fully-qualified class names, must implement ToolCalling.Tool
		public JsonObject Config;
	}

	public ToolCalling(ToolClass[] toolClasses) throws Exception {
		this.toolClasses = toolClasses;
		initializeTools();
	}

	// +-----------------+
	// | initializeTools |
	// +-----------------+

	private void initializeTools() throws Exception {

		if (toolClasses == null || toolClasses.length == 0) return;

		toolDescriptions = new ArrayList<JsonObject>();
		tools = new HashMap<String,Tool>();
		
		for (ToolClass toolClass : toolClasses) {

			Class<?> c = Class.forName(toolClass.ClassName);
			Tool tool = (ToolCalling.Tool) c.getDeclaredConstructor().newInstance();
			JsonObject description = tool.initialize(toolClass.Config);
			toolDescriptions.add(description);

			String name = description.getAsJsonObject("function").get("name").getAsString();
			tools.put(name, tool);
		}
	}

	// +-----------------+
	// | getDescriptions |
	// +-----------------+

	public List<JsonObject> getDescriptions() {
		return(toolDescriptions);
	}

	// +-------------+
	// | call(Async) |
	// +-------------+
	
	public CompletableFuture<String> callAsync(String name, String arguments, Utility utils) {
		return(utils.getExec().runAsync("ToolCalling.callAsync", new Exec.AsyncOperation() {
			public String execute() throws Exception {
				return(call(name, arguments, utils));
			}
		}));
	}

	public String call(String name, String arguments, Utility utils) throws Exception {
		JsonObject jsonArgs = JsonParser.parseString(arguments).getAsJsonObject();
		return(tools.get(name).execute(jsonArgs, utils));
	}
	
	// +----------------+
	// | Tool Interface |
	// +----------------+

	interface Tool {
		public JsonObject initialize(JsonObject jsonConfig); // return tool description
		public String execute(JsonObject arguments, Utility utils) throws Exception;
	}

	// +----------------+
	// | WebSearch_Tool |
	// +----------------+

	public static class WebSearch_Tool implements Tool
	{
		public static class Config
		{
			public Integer MaxResults = 12;
			public String UrlPrefix = "http://localhost:3001/search?format=json&q=";
		}

		public JsonObject initialize(JsonObject jsonConfig) {
			try {
				this.cfg = (jsonConfig == null ? new Config() : new Gson().fromJson(jsonConfig.toString(), Config.class));
				return(JsonParser.parseString(Easy.stringFromResource("websearch_tool.json")).getAsJsonObject());
			}
			catch (Exception e) {
				log.severe("Can't find WebSearch_Tool description resource");
				return(null);
			}
		}
		
		public String execute(JsonObject arguments, Utility utils) throws Exception {

			String query = arguments.get("query").getAsString();

			int max = cfg.MaxResults;
			if (arguments.has("max_results")) max = arguments.get("max_results").getAsInt();
			
			String url = cfg.UrlPrefix + Easy.urlEncode(query);
			
			WebRequests.Response response = utils.getRequests().fetch(url);
			return(response.successful() ? makeResults(response, max) : makeError(response, utils));
		}

		private String makeResults(WebRequests.Response response, int max) {

			JsonArray results = JsonParser.parseString(response.Body).getAsJsonObject().get("results").getAsJsonArray();
			JsonArray transformed = new JsonArray();

			int count = (results.size() > max ? max : results.size());
			for (int i = 0; i < count; ++i) {
				
				JsonObject json = new JsonObject();
				transformed.add(json);

				JsonObject result = results.get(i).getAsJsonObject();
				json.add("url", result.get("url"));
				json.add("title", result.get("title"));
				json.add("content", result.get("content"));
			}

			return(transformed.toString());
		}

		private String makeError(WebRequests.Response response, Utility utils) {
			JsonObject err = new JsonObject();
			err.addProperty("result", "ERROR");
			err.addProperty("http_status", response.Status);
			err.addProperty("http_status_text", response.StatusText);
			if (response.Ex != null) err.addProperty("exception", response.Ex.toString());
			return(utils.getGson().toJson(err));
		}

		private Config cfg;
 	}

	// +---------+
	// | Members |
	// +---------+

	public ToolClass[] toolClasses;
	public List<JsonObject> toolDescriptions;
	public Map<String,Tool> tools;
	
	private final static Logger log = Logger.getLogger(ToolCalling.class.getName());
}
