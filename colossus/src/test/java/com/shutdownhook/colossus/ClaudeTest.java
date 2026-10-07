//
// CLAUDETEST.JAVA
//

package com.shutdownhook.colossus;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClaudeTest
{
	private static final Gson gson = new Gson();

	// +--------------------------+
	// | serializeRequest helpers |
	// +--------------------------+

	private OpenAI.Request makeRequest(String model, OpenAI.Message... msgs) {
		OpenAI.Request req = new OpenAI.Request();
		req.model = model;
		req.stream = false;
		req.max_tokens = 1024L;
		req.messages = new ArrayList<OpenAI.Message>();
		for (OpenAI.Message m : msgs) req.messages.add(m);
		return(req);
	}

	private OpenAI.Message makeMsg(String role, String content) {
		OpenAI.Message msg = new OpenAI.Message();
		msg.role = role;
		msg.content = content;
		return(msg);
	}

	private OpenAI.Message makeToolMsg(String content, String toolCallId, String name) {
		OpenAI.Message msg = makeMsg(OpenAI.ROLE_TOOL, content);
		msg.tool_call_id = toolCallId;
		msg.name = name;
		return(msg);
	}

	private OpenAI.Message makeAsstWithToolCalls(String content, String tcId, String tcName, String tcArgs) {
		OpenAI.Message msg = makeMsg(OpenAI.ROLE_ASST, content);
		msg.tool_calls = new ArrayList<OpenAI.ToolCall>();
		OpenAI.ToolCall tc = new OpenAI.ToolCall();
		tc.id = tcId;
		tc.type = "function";
		tc.function = new OpenAI.FunctionCall();
		tc.function.name = tcName;
		tc.function.arguments = tcArgs;
		msg.tool_calls.add(tc);
		return(msg);
	}

	// +----------------------------------+
	// | serializeRequest: basic messages |
	// +----------------------------------+

	@Test
	public void testSerialize_SystemExtracted() {
		OpenAI.Request req = makeRequest("claude-sonnet",
			makeMsg(OpenAI.ROLE_SYSTEM, "You are helpful."),
			makeMsg(OpenAI.ROLE_USER, "Hi"));

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, false, gson)).getAsJsonObject();

		assertEquals("You are helpful.", result.get("system").getAsString());
		assertEquals(1, result.getAsJsonArray("messages").size());
		assertEquals("user", result.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
		assertNull(result.get("cache_control"));
	}

	@Test
	public void testSerialize_UserAndAssistant() {
		OpenAI.Request req = makeRequest("claude-sonnet",
			makeMsg(OpenAI.ROLE_USER, "Hello"),
			makeMsg(OpenAI.ROLE_ASST, "Hi there"));

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, false, gson)).getAsJsonObject();
		JsonArray messages = result.getAsJsonArray("messages");

		assertEquals(2, messages.size());
		assertEquals("Hello", messages.get(0).getAsJsonObject().get("content").getAsString());
		assertEquals("Hi there", messages.get(1).getAsJsonObject().get("content").getAsString());
	}

	@Test
	public void testSerialize_ModelAndParams() {
		OpenAI.Request req = makeRequest("claude-sonnet", makeMsg(OpenAI.ROLE_USER, "Hi"));
		req.temperature = 0.5;

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, true, gson)).getAsJsonObject();

		assertEquals("claude-sonnet", result.get("model").getAsString());
		assertEquals(1024, result.get("max_tokens").getAsLong());
		assertEquals(0.5, result.get("temperature").getAsDouble(), 0.001);
		assertFalse(result.get("stream").getAsBoolean());
		assertEquals("ephemeral", result.get("cache_control").getAsJsonObject().get("type").getAsString());
	}

	// +------------------------------------+
	// | serializeRequest: tool use / calls |
	// +------------------------------------+

	@Test
	public void testSerialize_AssistantToolCalls() {
		OpenAI.Request req = makeRequest("claude-sonnet",
			makeMsg(OpenAI.ROLE_USER, "Search for cats"),
			makeAsstWithToolCalls("Let me search.", "call_1", "web_search", "{\"query\":\"cats\"}"));

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, false, gson)).getAsJsonObject();
		JsonArray messages = result.getAsJsonArray("messages");
		JsonObject asstMsg = messages.get(1).getAsJsonObject();
		JsonArray content = asstMsg.getAsJsonArray("content");

		assertEquals(2, content.size());

		JsonObject textBlock = content.get(0).getAsJsonObject();
		assertEquals("text", textBlock.get("type").getAsString());
		assertEquals("Let me search.", textBlock.get("text").getAsString());

		JsonObject toolUse = content.get(1).getAsJsonObject();
		assertEquals("tool_use", toolUse.get("type").getAsString());
		assertEquals("call_1", toolUse.get("id").getAsString());
		assertEquals("web_search", toolUse.get("name").getAsString());
		assertEquals("cats", toolUse.getAsJsonObject("input").get("query").getAsString());
	}

	@Test
	public void testSerialize_ToolResultsGrouped() {
		OpenAI.Request req = makeRequest("claude-sonnet",
			makeMsg(OpenAI.ROLE_USER, "Do two things"),
			makeAsstWithToolCalls(null, "call_1", "tool_a", "{}"),
			makeToolMsg("result A", "call_1", "tool_a"),
			makeToolMsg("result B", "call_2", "tool_b"));

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, false, gson)).getAsJsonObject();
		JsonArray messages = result.getAsJsonArray("messages");

		// user, assistant, user(tool_results)
		assertEquals(3, messages.size());

		JsonObject toolResultMsg = messages.get(2).getAsJsonObject();
		assertEquals("user", toolResultMsg.get("role").getAsString());

		JsonArray toolResults = toolResultMsg.getAsJsonArray("content");
		assertEquals(2, toolResults.size());
		assertEquals("tool_result", toolResults.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("call_1", toolResults.get(0).getAsJsonObject().get("tool_use_id").getAsString());
		assertEquals("result A", toolResults.get(0).getAsJsonObject().get("content").getAsString());
		assertEquals("call_2", toolResults.get(1).getAsJsonObject().get("tool_use_id").getAsString());
	}

	// +-------------------------------------+
	// | serializeRequest: tool descriptions |
	// +-------------------------------------+

	@Test
	public void testSerialize_ToolDescriptions() {
		OpenAI.Request req = makeRequest("claude-sonnet", makeMsg(OpenAI.ROLE_USER, "Hi"));
		req.tools = new ArrayList<JsonObject>();
		req.tools.add(JsonParser.parseString(
			"{\"type\":\"function\",\"function\":{\"name\":\"web_search\"," +
			"\"description\":\"Search the web\",\"parameters\":{\"type\":\"object\"," +
			"\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}}}").getAsJsonObject());

		JsonObject result = JsonParser.parseString(Claude.serializeRequest(req, false, gson)).getAsJsonObject();
		JsonArray tools = result.getAsJsonArray("tools");

		assertEquals(1, tools.size());
		JsonObject tool = tools.get(0).getAsJsonObject();
		assertEquals("web_search", tool.get("name").getAsString());
		assertEquals("Search the web", tool.get("description").getAsString());
		assertTrue(tool.has("input_schema"));
		assertFalse(tool.has("type"));
		assertFalse(tool.has("function"));
	}

	// +------------------------------+
	// | deserializeResponse: basics  |
	// +------------------------------+

	@Test
	public void testDeserialize_TextResponse() {
		String json = "{\"id\":\"msg_123\",\"model\":\"claude-sonnet\",\"stop_reason\":\"end_turn\"," +
			"\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}]," +
			"\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

		OpenAI.Response resp = Claude.deserializeResponse(json, gson).Response;

		assertEquals("msg_123", resp.id);
		assertEquals("claude-sonnet", resp.model);
		assertEquals(1, resp.choices.length);
		assertEquals(OpenAI.FINISH_REASON_OK, resp.choices[0].finish_reason);
		assertEquals("Hello!", resp.choices[0].message.content);
		assertEquals(OpenAI.ROLE_ASST, resp.choices[0].message.role);
		assertNull(resp.choices[0].message.tool_calls);
		assertNull(resp.choices[0].message.reasoning_content);
		assertEquals(Integer.valueOf(10), resp.usage.prompt_tokens);
		assertEquals(Integer.valueOf(5), resp.usage.completion_tokens);
		assertEquals(Integer.valueOf(15), resp.usage.total_tokens);
	}

	@Test
	public void testDeserialize_CacheTokensIncludedInPromptTokens() {
		String json = "{\"id\":\"msg_1\",\"model\":\"m\",\"stop_reason\":\"end_turn\"," +
			"\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]," +
			"\"usage\":{\"input_tokens\":100,\"output_tokens\":20," +
			"\"cache_read_input_tokens\":50,\"cache_creation_input_tokens\":30}}";

		OpenAI.Response resp = Claude.deserializeResponse(json, gson).Response;

		assertEquals(Integer.valueOf(180), resp.usage.prompt_tokens);
		assertEquals(Integer.valueOf(20), resp.usage.completion_tokens);
		assertEquals(Integer.valueOf(200), resp.usage.total_tokens);
	}

	@Test
	public void testDeserialize_MaxTokensTruncation() {
		String json = "{\"id\":\"msg_1\",\"model\":\"m\",\"stop_reason\":\"max_tokens\"," +
			"\"content\":[{\"type\":\"text\",\"text\":\"partial\"}]," +
			"\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

		OpenAI.Response resp = Claude.deserializeResponse(json, gson).Response;
		assertEquals(OpenAI.FINISH_REASON_TRUNC, resp.choices[0].finish_reason);
	}

	// +------------------------------------+
	// | deserializeResponse: tool use      |
	// +------------------------------------+

	@Test
	public void testDeserialize_ToolUse() {
		String json = "{\"id\":\"msg_1\",\"model\":\"m\",\"stop_reason\":\"tool_use\"," +
			"\"content\":[{\"type\":\"text\",\"text\":\"Let me search.\"}," +
			"{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"web_search\",\"input\":{\"query\":\"cats\"}}]," +
			"\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}";

		OpenAI.Response resp = Claude.deserializeResponse(json, gson).Response;

		assertEquals(OpenAI.FINISH_REASON_TOOLS, resp.choices[0].finish_reason);
		assertEquals("Let me search.", resp.choices[0].message.content);

		List<OpenAI.ToolCall> tcs = resp.choices[0].message.tool_calls;
		assertNotNull(tcs);
		assertEquals(1, tcs.size());
		assertEquals("toolu_1", tcs.get(0).id);
		assertEquals("function", tcs.get(0).type);
		assertEquals("web_search", tcs.get(0).function.name);
		assertTrue(tcs.get(0).function.arguments.contains("\"cats\""));
	}

	// +------------------------------------+
	// | deserializeResponse: thinking      |
	// +------------------------------------+

	@Test
	public void testDeserialize_Thinking() {
		String json = "{\"id\":\"msg_1\",\"model\":\"m\",\"stop_reason\":\"end_turn\"," +
			"\"content\":[{\"type\":\"thinking\",\"thinking\":\"Let me think...\"}," +
			"{\"type\":\"text\",\"text\":\"The answer is 42.\"}]," +
			"\"usage\":{\"input_tokens\":5,\"output_tokens\":10}}";

		OpenAI.Response resp = Claude.deserializeResponse(json, gson).Response;

		assertEquals("The answer is 42.", resp.choices[0].message.content);
		assertEquals("Let me think...", resp.choices[0].message.reasoning_content);
	}
}
