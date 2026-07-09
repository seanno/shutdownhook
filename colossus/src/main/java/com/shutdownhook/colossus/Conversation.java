//
// CONVERSATION.JAVA
// OpenAI formats (with some llama.cpp dependency where noted)
//

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
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
		public Environment.Config Environment = new Environment.Config();

		public String SystemPrompt;
		
		public double Temperature = 0.0d;
		public long MaxTokens = 4096; 
		public long MaxTokensCeilingDivisor = 4; // context length / divisor == ceiling
		
		public int PruneTruncationLength = 100;

		public String MinjaRenderPath = "~/.local/bin/minja_render";
		
		public String CompletionPath = "/v1/chat/completions";
		public String PropsPathPrefix = "/props?model=";
		public String TokenizePath = "/tokenize";

		public String ConversationTooLargeMsg =
			"This conversation has grown too large to fit into model context, even after " +
			"attempts to prune old content. Please start a new conversation to continue.";

		public String SummarizePrompt =
			"Summarize the provided text. Your summary should be as short as possible " +
			"while maintaining enough fidelity to be useful as a substitute for the text " +
			"in a chat history. The target audience is you, not a human reader, so use " +
			"shorthand and eliminate grammatical constructions that inflate reponse size " +
			"without adding content value.";

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}

		public String toJson() {
			return(new Gson().toJson(this));
		}

		public Config clone() {
			return(fromJson(toJson())); // round trip for clean clone
		}

		public Config override(Path overridePath) throws Exception {
			
			if (!Files.exists(overridePath)) return(clone());
			
			JsonObject thisCfg = JsonParser.parseString(toJson()).getAsJsonObject();

			String overrideJson = Easy.stringFromFile(overridePath.toString());
			JsonObject overrideCfg = JsonParser.parseString(overrideJson).getAsJsonObject();

			for (String key : overrideCfg.keySet()) thisCfg.put(key, overrideCfg.get(key));

			return(fromJson(thisCfg.toString()));
		}
	}

	public Conversation(Config cfg) throws Exception {
		this.cfg = cfg;
		this.environment = new Environment(cfg.Environment);
		this.utils = new Utility(cfg.Utility);
		this.toolCalling = new ToolCalling(cfg.ToolClasses, this);
		this.reset();
		this.setupModelProps();
	}

	public void close() {
		utils.close();
	}

	public Config getConfig() { return(cfg); }
	public Utility getUtils() { return(utils); }
	public Environment getEnv() { return(environment); }

	// +--------+
	// | prompt |
	// +--------+

	public String safePrompt(String input) {
		try { 
			return(prompt(input));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "safePrompt", true));
			return(null);
		}
	}
	
	public String prompt(String input) throws Exception {
		maxTokensEffective = cfg.MaxTokens;
		String response = promptOne(input);
		while (response == null) response = promptOne(null);
		return(response);
	}
	
	private String promptOne(String input) throws Exception {

		String request = makeRequestBody(input);
		if (request == null) return(cfg.ConversationTooLargeMsg);
		
		String body = sendRequest(cfg.CompletionPath, request);
		Response response = utils.getGson().fromJson(body, Response.class);

		if (response.choices == null || response.choices.length != 1) {
			throw new Exception(String.format("No unique choice in response: %s", body));
		}

		Choice choice = response.choices[0];
		messageHistory.add(choice.message);

		log.info(String.format("Stats: prompt: %d, completion: %d, total: %d, PPS: %f",
							   response.usage.prompt_tokens,
							   response.usage.completion_tokens,
							   response.usage.total_tokens,
							   response.timings.predicted_per_second));

		String tag = "";
		
		switch (choice.finish_reason) {

			case FINISH_REASON_TOOLS:
				callTools(choice.message.tool_calls);
				return(null);
				
			case FINISH_REASON_TRUNC:
				log.info(String.format("Hit generation max: %d", maxTokensEffective));
				if (tryIncreaseMaxTokens()) return(null);
				log.warning("Unable to increase max tokens; terminating prompt.");
				tag = CONTENT_TRUNCATED;
				break;

			case FINISH_REASON_FILTER:
				log.warning("OOPS: Hit filter");
				tag = CONTENT_FILTERED;
				break;

			default:
			case FINISH_REASON_OK:
				if (Easy.nullOrEmpty(choice.message.content)) tag = CONTENT_EMPTY;
				break;
		}

		return(String.format("%s%s", tag, choice.message.content));
	}

	// +------------+
	// | getHistory |
	// +------------+

	public String history() {
		return(utils.getGson().toJson(messageHistory));
	}

	// +-------+
	// | reset |
	// +-------+

	public void reset() {
		this.messageHistory = new ArrayList<Message>();
	}

	// +-----------+
	// | summarize |
	// +-----------+

	public String summarize(String input) {

		Conversation.Config summaryCfg = cfg.clone();
		summaryCfg.SystemPrompt = cfg.SummarizePrompt;
		Conversation summaryConversation = null;
		
		try {
			summaryConversation = new Conversation(summaryCfg);
			return(summaryConversation.prompt(input));
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "summarize", true));
			return(input);
		}
		finally {
			Easy.safeClose(summaryConversation);
		}
	}

	// +-----------------+
	// | setupModelProps |
	// +-----------------+

	// LLAMA.CPP-Specific! Note the ModelProps structure is in no way exhaustive; I've just
	// picked out the pieces I care about ... easy to add others as needed

	public static class GenerationSettings
	{
		public long n_ctx;
	}
	
	public static class ModelProps
	{
		public GenerationSettings default_generation_settings;
		public String model_alias;
		public String chat_template;
		public String bos_token;
		public String eos_token;
	}

	private void setupModelProps() throws Exception {
		String body = sendRequest(cfg.PropsPathPrefix + Easy.urlEncode(cfg.Model), null);
		this.modelProps = utils.getGson().fromJson(body, ModelProps.class);
		this.modelPropsRaw = JsonParser.parseString(body).getAsJsonObject();
	}

	public long getContextLength() { return(modelProps.default_generation_settings.n_ctx); }
	public ModelProps getModelProps() { return(modelProps); }
	public JsonObject getModelPropsRaw() { return(modelPropsRaw); }

	// +---------------+
	// | getTokenCount |
	// +---------------+

	// LLAMA.CPP-Specific!

	private long getTokenCount(Request req) {
		String templated = applyModelTemplate(req);
		if (templated == null) return(0L); // degenerate case
		return(getTokenCount(templated));
	}

	private long getTokenCount(String input) {
		
		String post = null;
		
		try {
			JsonObject jsonPost = new JsonObject();
			jsonPost.addProperty("model", cfg.Model);
			jsonPost.addProperty("content", input);

			post = jsonPost.toString();
			String body = sendRequest(cfg.TokenizePath, post);
			JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();

			return(jsonResponse.get("tokens").getAsJsonArray().size());
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "getTokenCount", true));
			return(0L);
		}
	}
	
	// +--------------------+
	// | applyModelTemplate |
	// +--------------------+

	// uses a callout to minja-render (cfg.MiniJinjaPath) to render a request into
	// a chat template so we can accurately measure its token count via getTokenCount().
	// note minja-render is buildable from shutdownhook/colossus/minja-render, you'll
	// need C++ and CMake.
	
	private String applyModelTemplate(Request req) {

		Path modelContextTemp = null;
		OutputStream os = null;

		try {
			// write out req as context object
			JsonObject context = JsonParser.parseString(utils.getCompactGson().toJson(req)).getAsJsonObject();
			context.addProperty("add_generation_prompt", true);
			context.addProperty("bos_token", modelProps.bos_token);
			context.addProperty("eos_token", modelProps.eos_token);

			modelContextTemp = Files.createTempFile("colossus", null);
			Easy.stringToFile(modelContextTemp.toString(), utils.getCompactGson().toJson(context));

			// start the process
			String home = System.getProperty("user.home");
			Path minja = Paths.get(cfg.MinjaRenderPath.replaceAll("~", home));

			String[] commands = new String[] { minja.toString(), "-", modelContextTemp.toString()};
			ProcessBuilder pb = new ProcessBuilder(commands).redirectErrorStream(false);
			Process p = pb.start();

			// write the template to STDIN
			os = p.getOutputStream();
			os.write(modelProps.chat_template.getBytes(StandardCharsets.UTF_8));
			os.flush(); os.close(); os = null;
			
			// and read the result
			return(Easy.stringFromInputStream(p.getInputStream()));
		}
		catch (Exception e) {
			log.warning(Easy.exMsg(e, "applyModelTemplate", true));
			return(null);
		}
		finally {
			Easy.safeClose(os);
			if (modelContextTemp != null) {
				try { Files.delete(modelContextTemp); }
				catch (Exception eFinal) { /* eat it */ }
			}
		}
	}

	// +-----------+
	// | callTools |
	// +-----------+

	private void callTools(List<ToolCall> toolCalls) throws Exception {

		// start the calls going
		List<CompletableFuture<String>> futures = new ArrayList<CompletableFuture<String>>();
		for (int i = 0; i < toolCalls.size(); ++i) {
			ToolCall call = toolCalls.get(i);
			futures.add(toolCalling.callAsync(call.function.name, call.function.arguments));
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
		if (maxTokensEffective > 0) req.max_tokens = maxTokensEffective;

		if (messageHistory.size() == 0) {
			StringBuilder sb = new StringBuilder();

			if (cfg.SystemPrompt != null) sb.append(cfg.SystemPrompt);
			sb.append(environment.getLocation());
			sb.append(environment.getTimeZone());

			messageHistory.add(makeSystemMessage(sb.toString()));
		}
		
		if (input != null) messageHistory.add(makeUserMessage(input));

		req.messages = messageHistory;
		req.tools = toolCalling.getDescriptions();

		if (!pruneRequest(req)) return(null);
		return(utils.getGson().toJson(req));
	}

	private Message makeUserMessage(String input) {
		return(makeMessage(ROLE_USER, environment.getTimeStamp() + input));
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

	// +--------------+
	// | pruneRequest |
	// +--------------+

	// this is a kind of ugly side-effect-y set of methods. We need the
	// full request to make judgments about token budget, but by changing
	// req.messages we change messageHistory as well. We'll just live with
	// this for now, and then forget we ever did it, and then get screwed
	// sometime in the future. Fun!
	//
	// TRUE return means all is well. FALSE means we couldn't gret small
	// enough to fit into our budget.

	private boolean pruneRequest(Request req) {

		long tokenBudget = getContextLength() - maxTokensEffective;
		if (tokenBudget <= 0) return(true); // degenerate case, just bail
		
		long requestTokens = getTokenCount(req);
		if (requestTokens <= tokenBudget) return(true);

		// 1. try to remove old tool calls --- this may not do much, but
		//    will help if e.g., we've been writing large files
		
		log.info(String.format("Request too large 1: %d, limit %d)", requestTokens, tokenBudget));
		boolean prunedSome = pruneToolRequests(req);

		if (prunedSome) requestTokens = getTokenCount(req);
		if (requestTokens <= tokenBudget) return(true);

		// 2. try to prune old tool responses, leaving the last instance of every tool
		
		log.info(String.format("Request too large 2: %d, limit %d)", requestTokens, tokenBudget));
		prunedSome = pruneToolResponses(req);

		if (prunedSome) requestTokens = getTokenCount(req);
		if (requestTokens <= tokenBudget) return(true);

		// Sad, nothing more to do.....
		
		log.warning(String.format("Unable to prune history below context limit: %d,%d",
								  requestTokens, tokenBudget));

		return(false);
	}

	// for all past tool requests in messages history with arguments larger than a given size,
	// replace with an empty object. Harder to "prune" this leaving some trace because it has
	// to remain json; this is a good easy approach.

	private boolean pruneToolRequests(Request req) {

		boolean prunedSome = false;
		
		for (int i = 0; i < req.messages.size(); ++i) {
			
			Message thisMsg = req.messages.get(i);
			if (!thisMsg.role.equals(ROLE_ASST)) continue;
			if (thisMsg.tool_calls == null || thisMsg.tool_calls.size() == 0) continue;
			
			for (ToolCall toolCall : thisMsg.tool_calls) {
				
				String args = toolCall.function.arguments;
				if (args != null && args.length() > cfg.PruneTruncationLength) {
					args = "{}";
					prunedSome = true;
				}
			}
		}

		return(prunedSome);
	}

	// try to prune all but the last response for every tool

	private final static String PRUNE_MARKER = "...PRUNED";
	
	private boolean pruneToolResponses(Request req) {

		boolean prunedSome = false;
		int realMaxLength = cfg.PruneTruncationLength + PRUNE_MARKER.length();
		Set<String> seen = new HashSet<String>();
		
		for (int i = req.messages.size() - 1; i >= 0; --i) {
			
			Message thisMsg = req.messages.get(i);
			if (!thisMsg.role.equals(ROLE_TOOL)) continue;

			if (seen.contains(thisMsg.name)) {
				// seen it before ... maybe prune
				String content = thisMsg.content;

				if (content != null && !content.endsWith(PRUNE_MARKER) && content.length() > realMaxLength) {

					thisMsg.content = content.substring(0, cfg.PruneTruncationLength) + PRUNE_MARKER;
					prunedSome = true;
				}
			}
			else {
				// first time, just remember it
				seen.add(thisMsg.name);
			}
		}

		return(prunedSome);
		
	}

	// +----------------------+
	// | tryIncreaseMaxTokens |
	// +----------------------+

	private boolean tryIncreaseMaxTokens() {
		
		if (maxTokensEffective == 0) return(false);

		long ceiling = getContextLength() / cfg.MaxTokensCeilingDivisor;
		if (maxTokensEffective >= ceiling) return(false);
		
		long newMaxTokens = maxTokensEffective + cfg.MaxTokens;

		maxTokensEffective = (newMaxTokens > ceiling ? ceiling : newMaxTokens);
		return(true);
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
		public Long max_tokens;

		// This lives here so we ensure we're using the same serialization
		// when computing token counts and actually submitting the request.
		public String toString() { return(new Gson().toJson(this)); }
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
						Easy.stringToFile("/tmp/conversation.dump", response);
						break;

					case "props":
						response = conversation.getModelPropsRaw().toString();
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
	private Environment environment;
	private Utility utils;
	private ToolCalling toolCalling;
	private List<Message> messageHistory;
	private long maxTokensEffective;

	private ModelProps modelProps;
	private JsonObject modelPropsRaw;
	
	private final static Logger log = Logger.getLogger(Conversation.class.getName());
}
