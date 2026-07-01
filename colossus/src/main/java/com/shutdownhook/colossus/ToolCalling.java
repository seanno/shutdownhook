//
// TOOLCALLING.JAVA
//

package com.shutdownhook.colossus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Exec;
import com.shutdownhook.toolbox.WebRequests;

public class ToolCalling
{
	public static class ToolClass
	{
		public String ClassName; // fully-qualified class names, must implement ToolCalling.Tool
		public String Name; // if present, overrides default name
		public String Description; // if present, overrides default description
		public boolean AutoPrune = false; // if true, past role=tool messages are redacted
		public JsonObject Config;
	}

	public ToolCalling(ToolClass[] toolClasses, Conversation conversation) throws Exception {
		this.toolClasses = toolClasses;
		this.conversation = conversation;
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
			JsonObject description = tool.initialize(toolClass, conversation);
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
	
	public CompletableFuture<String> callAsync(String name, String arguments) {
		return(conversation.getUtils().getExec().runAsync("ToolCalling.callAsync", new Exec.AsyncOperation() {
			public String execute() throws Exception {
				return(call(name, arguments));
			}
			public String exceptionResult() {
				return("An error occurred in this tool call.");
			}
		}));
	}

	public String call(String name, String arguments) throws Exception {
		JsonObject jsonArgs = JsonParser.parseString(arguments).getAsJsonObject();
		return(tools.get(name).execute(jsonArgs, conversation));
	}
	
	// +----------------+
	// | Tool Interface |
	// +----------------+

	interface Tool {
		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception; // return tool description
		public String execute(JsonObject arguments, Conversation conversation) throws Exception;
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

		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, Config.class);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@websearch_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {

			String query = arguments.get("query").getAsString();

			int max = cfg.MaxResults;
			if (arguments.has("max_results")) max = arguments.get("max_results").getAsInt();
			
			String url = cfg.UrlPrefix + Easy.urlEncode(query);
			
			WebRequests.Response response = conversation.getUtils().getRequests().fetch(url);
			if (!response.successful()) return(ToolCalling.makeWebErrorJson(response));

			return(makeResults(response, max));
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

		private Config cfg;
 	}

	// +-----------------+
	// | WebRequest_Tool |
	// +-----------------+

	public static class WebRequest_Tool implements Tool
	{
		public static class Config
		{
			public Integer MaxLength; // null means no truncation, bad idea
		}

		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, Config.class);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@webrequest_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {

			String url = arguments.get("url").getAsString();
			WebRequests.Response response = conversation.getUtils().getRequests().fetch(url);
			if (!response.successful()) return(ToolCalling.makeWebErrorJson(response));

			String body = response.Body;

			if (response.Headers.containsKey("Content-Type")) {
				String contentType = response.Headers.get("Content-Type").get(0).toLowerCase();
				if (contentType.startsWith("text/html")) body = extractTextFromHtml(body);
			}

			boolean truncated = false;
			int originalLength = body.length();
			
			if (cfg.MaxLength != null && originalLength > cfg.MaxLength) {
				body = body.substring(0, cfg.MaxLength);
				truncated = true;
			}

			JsonObject json = new JsonObject();
			json.addProperty("content", body);
			json.addProperty("truncated", truncated);
			json.addProperty("original_length", originalLength);

			return(json.toString());
		}

		private String extractTextFromHtml(String body) {
			try {
				Document doc = Jsoup.parse(body);
				String cleanText = doc.body().text();
				return(cleanText);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "jsoup", true));
				return(body);
			}
		}

		private Config cfg;
 	}

	// +------------------+
	// | Environment_Tool |
	// +------------------+

	public static class Environment_Tool implements Tool
	{
		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@environment_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {
			return(conversation.getEnv().getJson().toString());
		}

		private DateTimeFormatter dtf;
 	}

	// +---------------+
	// | SubAgent_Tool |
	// +---------------+

	public static class SubAgent_Tool implements Tool
	{
		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			mergeConfigOverrides(toolClass, conversation);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@subagent_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {
			Conversation subConvo = null;
			try {
				subConvo = new Conversation(cfg);
				return(subConvo.prompt(arguments.get("prompt").getAsString()));
			}
			finally {
				Easy.safeClose(subConvo);
			}
		}

		private void mergeConfigOverrides(ToolClass toolClass, Conversation conversation) {
			JsonObject base = JsonParser.parseString(conversation.getConfig().toJson()).getAsJsonObject();
			for (String key : toolClass.Config.keySet()) base.add(key, toolClass.Config.get(key));
			this.cfg = Conversation.Config.fromJson(base.toString());
		}

		private Conversation.Config cfg;
 	}

	// +---------+
	// | Helpers |
	// +---------+

	public static JsonObject getToolDescriptionFromSmartyPath(ToolClass toolClass, String path) throws Exception {

		String json = Easy.stringFromSmartyPath(path);
		JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();

		if (toolClass.Name != null) {
			jsonObj.get("function").getAsJsonObject().addProperty("name", toolClass.Name);
		}
		else {
			toolClass.Name = jsonObj.get("function").getAsJsonObject().get("name").getAsString();
		}
		
		if (toolClass.Description != null) {
			jsonObj.get("function").getAsJsonObject().addProperty("description", toolClass.Description);
		}
		else {
			toolClass.Description = jsonObj.get("function").getAsJsonObject().get("description").getAsString();
		}

		return(jsonObj);
	}

	public static <T> T loadConfig(ToolClass toolClass, Class<T> type) throws Exception {
		return(toolClass.Config == null ? type.getDeclaredConstructor().newInstance()
			   : new Gson().fromJson(toolClass.Config.toString(), type));
	}
	
	public static String makeWebErrorJson(WebRequests.Response response) {
		JsonObject err = new JsonObject();
		err.addProperty("result", "ERROR");
		err.addProperty("http_status", response.Status);
		err.addProperty("http_status_text", response.StatusText);
		if (response.Ex != null) err.addProperty("exception", response.Ex.toString());
		return(new Gson().toJson(err));
	}

	// +---------+
	// | Members |
	// +---------+

	public ToolClass[] toolClasses;
	public List<JsonObject> toolDescriptions;
	public Map<String,Tool> tools;
	public Conversation conversation;
	
	private final static Logger log = Logger.getLogger(ToolCalling.class.getName());
}
