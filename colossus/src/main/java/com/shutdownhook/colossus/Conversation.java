//
// CONVERSATION.JAVA
// OpenAI formats (with some llama.cpp dependency where noted)
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class Conversation implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String Model;

		public ToolCalling.ToolClass[] ToolClasses;
		public long ToolTimeoutMillis = (10 * 60 * 1000); // 10 minutes

		public String BaseUrl = "http://localhost:11434";
		public String ApiKey;
		
		public Utility.Config Utility = new Utility.Config();

		public String SystemPrompt;
		
		public double Temperature = 0.0d;
		public int MaxTokens = 0;

		public String CompletionPath = "/v1/chat/completions";
		public String PropsPathPrefix = "/props?model=";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Conversation(Config cfg) throws Exception {
		this.cfg = cfg;
		this.utils = new Utility(cfg.Utility);
		this.toolCalling = new ToolCalling(cfg.ToolClasses);
		this.reset();
	}

	public void close() {
		utils.close();
	}

	// +--------+
	// | prompt |
	// +--------+

	// note tihs will loop on tool_calls
	
	public String prompt(String input) throws Exception {
		
		String body = sendRequest(cfg.CompletionPath, makeRequestBody(input));
		Response response = utils.getGson().fromJson(body, Response.class);

		if (response.choices == null || response.choices.length != 1) {
			throw new Exception(String.format("No unqiue choice in response: %s", body));
		}

		Choice choice = response.choices[0];
		messageHistory.add(choice.message);

		System.out.println(String.format("Stats: prompt: %d, completion: %d, total: %d, PPS: %f",
							   response.usage.prompt_tokens,
							   response.usage.completion_tokens,
							   response.usage.total_tokens,
							   response.timings.predicted_per_second));

		String tag = "";
		
		switch (choice.finish_reason) {

			case FINISH_REASON_TOOLS:
				callTools(choice.message.tool_calls);
				return(prompt(null));
				
			case FINISH_REASON_TRUNC:
				tag = CONTENT_TRUNCATED;
				break;
				
			case FINISH_REASON_FILTER:
				tag = CONTENT_FILTERED;
				break;

			default:
			case FINISH_REASON_OK:
				if (Easy.nullOrEmpty(choice.message.content)) tag = CONTENT_EMPTY;
				break;
		}

		return(String.format("%s%s", tag, choice.message.content));
	}

	// +-------+
	// | reset |
	// +-------+

	public void reset() {
		this.messageHistory = new ArrayList<Message>();
	}

	// +------------------+
	// | getModelProps    |
	// | getContextLength |
	// +------------------+

	// LLAMA.CPP-Specific!

	public JsonObject getModelProps() throws Exception {
		String body = sendRequest(cfg.PropsPathPrefix + Easy.urlEncode(cfg.Model), null);
		return(JsonParser.parseString(body).getAsJsonObject());
	}

	public long getContextLength() throws Exception {
		
		JsonObject props = getModelProps();
		return(props.getAsJsonObject("default_generation_settings")
			   .get("n_ctx").getAsLong());
	}

	// +-----------+
	// | callTools |
	// +-----------+

	private void callTools(List<ToolCall> toolCalls) throws Exception {

		// start the calls going
		List<CompletableFuture<String>> futures = new ArrayList<CompletableFuture<String>>();
		for (int i = 0; i < toolCalls.size(); ++i) {
			ToolCall call = toolCalls.get(i);
			futures.add(toolCalling.callAsync(call.function.name, call.function.arguments, utils));
		}

		// add the results to message history --- walking in order so the results are presented
		// in request order (important for ollama even if it is pretending to be openai)
		
		for (int i = 0; i < toolCalls.size(); ++i) {
			
			ToolCall call = toolCalls.get(i);
			String content = futures.get(i).get(cfg.ToolTimeoutMillis, TimeUnit.MILLISECONDS);
			
			Message msg = makeToolMessage(content, call.id, call.function.name);
			messageHistory.add(msg);
		}
	}

	// +-----------------+
	// | Message Helpers |
	// +-----------------+

	private String sendRequest(String path, String json) throws Exception {
		
		WebRequests.Params webParams = new WebRequests.Params();

		if (json != null) {
			webParams.Body = json;
			webParams.setContentType("application/json");
		}
		
		if (cfg.ApiKey != null) {
			webParams.addHeader("Authorization", "Bearer " + cfg.ApiKey);
		}

		String url = Easy.urlPaste(cfg.BaseUrl, path);
		WebRequests.Response webResponse = utils.getRequests().fetch(url, webParams);
		
		if (!webResponse.successful()) webResponse.throwException("completion");

		return(webResponse.Body);
	}
	
	private String makeRequestBody(String input) {
		
		Request req = new Request();
		
		req.model = cfg.Model;
		req.stream = false;
		
		if (cfg.Temperature > 0.0) req.temperature = cfg.Temperature;
		if (cfg.MaxTokens > 0) req.max_tokens = cfg.MaxTokens;

		if (messageHistory.size() == 0 && cfg.SystemPrompt != null) {
			messageHistory.add(makeSystemMessage(cfg.SystemPrompt));
		}
		
		if (input != null) messageHistory.add(makeUserMessage(input));

		req.messages = messageHistory;
		req.tools = toolCalling.getDescriptions();

		return(utils.getGson().toJson(req));
	}

	private Message makeUserMessage(String input) {
		return(makeMessage(ROLE_USER, input));
	}

	private Message makeSystemMessage(String input) {
		return(makeMessage(ROLE_SYSTEM, input));
	}

	private Message makeToolMessage(String content, String id, String name) {
		Message msg = makeMessage(ROLE_TOOL, content);
		msg.tool_call_id = id;
		msg.name = name;
		return(msg);
	}

	private Message makeMessage(String role, String input) {
		Message msg = new Message();
		msg.role = role;
		msg.content = input;
		return(msg);
	}

	// +------------------------+
	// | OpenAI JSON Structures |
	// +------------------------+

	private static final String ROLE_USER = "user";
	private static final String ROLE_ASST = "assistant";
	private static final String ROLE_TOOL = "tool";
	private static final String ROLE_SYSTEM = "system";

	private static final String FINISH_REASON_OK = "stop";
	private static final String FINISH_REASON_TOOLS = "tool_calls";
	private static final String FINISH_REASON_TRUNC = "length";
	private static final String FINISH_REASON_FILTER = "content_filter";

	private static final String CONTENT_TRUNCATED = "[TRUNCATED] ";
	private static final String CONTENT_FILTERED = "[FILTERED] ";
	private static final String CONTENT_EMPTY = "[EMPTY] ";
	
	public static class FunctionCall
	{
		public String name;
		public String arguments;
	}
	
	public static class ToolCall
	{
		public String id;
		public String type;
		public FunctionCall function;
	}
	
	public static class Message
	{
		public String role;
		public String tool_call_id; // when role == "tool"
		public String name; // when role == "tool"
		public String content;
		public List<ToolCall> tool_calls;
	}

	public static class Request
	{
		public String model;
		public List<Message> messages;
		public List<JsonObject> tools;
		public Boolean stream;
		public Double temperature;
		public Integer max_tokens;
	}

	public static class Choice
	{
		public Integer index;
		public Message message;
		public String finish_reason;
	}

	public static class Usage
	{
		public Integer prompt_tokens;
		public Integer completion_tokens;
		public Integer total_tokens;
	}

	public static class Timings
	{
		public Integer cache_n;
		public Integer prompt_n;
		public Double prompt_ms;
		public Double prompt_per_token_ms;
		public Double prompt_per_second;
		public Integer predicted_n;
		public Double predicted_ms;
		public Double predicted_per_token_ms;
		public Double predicted_per_second;
	}

	public static class Response
	{
		public String id;
		public String object;
		public Long created;
		public String model;
		public Choice[] choices;
		public Usage usage;
		public Timings timings;
	}

	// +------------+
	// | Entrypoint |
	// +------------+
	
	public static void main(String[] args) throws Exception {

		Config cfg = Config.fromJson(Easy.stringFromFile(args[0]));
		Conversation conversation = new Conversation(cfg);

		System.out.println("Use 'exit' to exit, 'reset' to reset conversation, 'dump' for history...");
		Scanner scanner = new Scanner(System.in);

		try {
			boolean quit = false;
			while (!quit) {
				
				System.out.print("\n> ");
				String prompt = scanner.nextLine();
				if (prompt == null) continue;

				String lower = prompt.trim().toLowerCase();
				String response = "";

				switch (lower) {
					case "":
						break;
						
					case "exit":
					case "quit":
						quit = true;
						break;

					case "reset":
						conversation.reset();
						response = "converation reset";
						break;

					case "dump":
						response = conversation.toString();
						break;

					case "props":
						response = conversation.getModelProps().toString();
						break;

					default:
						response = conversation.prompt(prompt);
						break;
				}
				
				System.out.println(response);
			}
		}
		finally {
			scanner.close();
			conversation.close();
		}
	}

	// +-------+
	// | Debug |
	// +-------+

	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();

		sb.append("===== CONFIG\n");
		sb.append(utils.getGson().toJson(cfg));
		sb.append("\n===== TOOLS\n");
		sb.append(utils.getGson().toJson(toolCalling.getDescriptions()));
		sb.append("\n===== MESSAGE HISTORY\n");

		for (Message msg : messageHistory) {
			sb.append(utils.getGson().toJson(msg));
			sb.append("-----\n");
		}

		return(sb.toString());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Utility utils;
	private ToolCalling toolCalling;
	private List<Message> messageHistory;
	
	private final static Logger log = Logger.getLogger(Conversation.class.getName());
}
