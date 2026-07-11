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
import com.google.gson.JsonElement;
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
		public String Description; // if present, APPENDED to default description
		public JsonObject Config;

		public static ToolClass fromJson(String json) {
			return(new Gson().fromJson(json, ToolClass.class));
		}

		public String toJson() {
			return(new Gson().toJson(this));
		}

		public ToolClass clone() {
			return(fromJson(toJson())); // round trip for clean clone
		}
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
		return(conversation.getUtils().getExec().runAsyncEx("ToolCalling.callAsync", new Exec.AsyncOperationEx() {
			public String execute() throws Exception {
				return(call(name, arguments));
			}
			public String exceptionResult(Exception e) {
				return("An error occurred: " + e.getClass().getName());
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

			String query = getStringField(arguments, "query");
			int max = getIntegerField(arguments, "max_results", cfg.MaxResults);
			
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

			String url = getStringField(arguments, "url");
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

	// +------------+
	// | Files_Tool |
	// +------------+

	public static class Files_Tool implements Tool
	{
		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, TextFiles.Config.class);
			this.txt = new TextFiles(this.cfg);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@files_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {

			String cmd = getStringField(arguments, "cmd", "").toLowerCase();
			String path = getStringField(arguments, "path");

			if (Easy.nullOrEmpty(cmd) || Easy.nullOrEmpty(path)) {
				return("Error: cmd and path must be present");
			}

			switch (cmd) {
				case "list":
					int max = getIntegerField(arguments, "max_files", 0);
					List<TextFiles.FileInfo> infos = txt.listFileInfos(path, max);
					return(conversation.getUtils().getCompactGson().toJson(infos));

				case "read":
					int ichStart = getIntegerField(arguments, "start_index", 0);
					int cch = getIntegerField(arguments, "read_length", 0);
					TextFiles.ReadInfo info = txt.read(path, ichStart, cch);
					return(conversation.getUtils().getCompactGson().toJson(info));

				case "grep":
					String regex = getStringField(arguments, "regex");
					boolean caseInsensitive = getBooleanField(arguments, "case_insensitive", false);
					return(txt.grep(path, regex, caseInsensitive));

				case "write":
					String contentsWrite = getStringField(arguments, "contents");
					TextFiles.WriteInfo infoWrite = txt.put(path, contentsWrite);
					return(conversation.getUtils().getCompactGson().toJson(infoWrite));

				case "append":
					String contentsAppend = getStringField(arguments, "contents");
					TextFiles.WriteInfo infoAppend = txt.append(path, contentsAppend);
					return(conversation.getUtils().getCompactGson().toJson(infoAppend));

				case "download":
					String url = getStringField(arguments, "url");
					if (Easy.nullOrEmpty(url)) return("Error: url must be present");

					boolean extractText = getBooleanField(arguments, "extract_text", true);
					long cchDownloaded = txt.download(path, url, extractText, conversation.getUtils().getRequests());
					return(String.format("{ \"Length\": %d }", cchDownloaded));

				default:
					throw new Exception("unknown file_tool cmd: " + cmd);
			}
		}

		private TextFiles.Config cfg;
		private TextFiles txt;
 	}

	// +-----------+
	// | Code_Tool |
	// +-----------+

	public static class Code_Tool implements Tool
	{
		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, CodeSandbox.Config.class);
			this.code = new CodeSandbox(this.cfg);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@code_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {
			String cmd = getStringField(arguments, "cmd");
			return(code.run(cmd));
		}

		private CodeSandbox.Config cfg;
		private CodeSandbox code;
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
				return(subConvo.prompt(getStringField(arguments, "prompt")));
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
		JsonObject jsonFunc = jsonObj.get("function").getAsJsonObject();

		if (toolClass.Name != null) jsonFunc.addProperty("name", toolClass.Name);
		
		if (toolClass.Description != null) {
			String baseDescription = jsonObj.get("function").getAsJsonObject().get("description").getAsString();
			jsonFunc.addProperty("description", baseDescription + "\n" + toolClass.Description);
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

	public static String getStringField(JsonObject json, String field) {
		return(getStringField(json, field, null));
	}
	
	public static String getStringField(JsonObject json, String field, String defaultVal) {
		JsonElement child = json.get(field);
		if (child == null) return(defaultVal);
		return(child.getAsString());
	}

	public static int getIntegerField(JsonObject json, String field) {
		return(getIntegerField(json, field ,0));
	}
	
	public static int getIntegerField(JsonObject json, String field, int defaultVal) {
		JsonElement child = json.get(field);
		if (child == null) return(defaultVal);
		return(child.getAsInt());
	}

	public static boolean getBooleanField(JsonObject json, String field) {
		return(getBooleanField(json, field, false));
	}
	
	public static boolean getBooleanField(JsonObject json, String field, boolean defaultVal) {
		JsonElement child = json.get(field);
		if (child == null) return(defaultVal);
		return(child.getAsBoolean());
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
