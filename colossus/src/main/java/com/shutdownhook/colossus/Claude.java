//
// CLAUDE.JAVA
// Claude Messages API wire format transformations
//

package com.shutdownhook.colossus;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Claude
{
	// +------------------+
	// | serializeRequest |
	// +------------------+

	public static String serializeRequest(OpenAI.Request request, boolean automaticCaching, Gson gson) {

		JsonObject obj = new JsonObject();

		obj.addProperty("model", request.model);

		if (request.stream != null) obj.addProperty("stream", request.stream);
		if (request.temperature != null) obj.addProperty("temperature", request.temperature);
		if (request.max_tokens != null) obj.addProperty("max_tokens", request.max_tokens);

		if (automaticCaching) {
			JsonObject cacheControl = new JsonObject();
			cacheControl.addProperty("type", "ephemeral");
			obj.add("cache_control", cacheControl);
		}

		// extract system message and convert the rest
		JsonArray messages = new JsonArray();

		int i = 0;
		while (i < request.messages.size()) {
			OpenAI.Message msg = request.messages.get(i);

			if (msg.role.equals(OpenAI.ROLE_SYSTEM)) {
				obj.addProperty("system", msg.content);
				i++;
				continue;
			}

			if (msg.role.equals(OpenAI.ROLE_ASST)) {
				messages.add(convertAssistantMessage(msg));
				i++;
				continue;
			}

			if (msg.role.equals(OpenAI.ROLE_TOOL)) {
				// collect consecutive tool results into one user message
				JsonArray toolResults = new JsonArray();
				while (i < request.messages.size() &&
					   request.messages.get(i).role.equals(OpenAI.ROLE_TOOL)) {
					toolResults.add(convertToolResult(request.messages.get(i)));
					i++;
				}
				JsonObject userMsg = new JsonObject();
				userMsg.addProperty("role", "user");
				userMsg.add("content", toolResults);
				messages.add(userMsg);
				continue;
			}

			// user (or anything else)
			JsonObject userMsg = new JsonObject();
			userMsg.addProperty("role", "user");
			userMsg.addProperty("content", msg.content != null ? msg.content : "");
			messages.add(userMsg);
			i++;
		}

		obj.add("messages", messages);

		// convert tools
		if (request.tools != null && !request.tools.isEmpty()) {
			JsonArray tools = new JsonArray();
			for (JsonObject tool : request.tools) {
				tools.add(convertTool(tool));
			}
			obj.add("tools", tools);
		}

		return(gson.toJson(obj));
	}

	// +-------------------------+
	// | convertAssistantMessage |
	// +-------------------------+

	private static JsonObject convertAssistantMessage(OpenAI.Message msg) {

		JsonObject obj = new JsonObject();
		obj.addProperty("role", "assistant");

		if (msg.tool_calls != null && msg.tool_calls.size() > 0) {
			JsonArray content = new JsonArray();

			if (msg.content != null && msg.content.length() > 0) {
				JsonObject textBlock = new JsonObject();
				textBlock.addProperty("type", "text");
				textBlock.addProperty("text", msg.content);
				content.add(textBlock);
			}

			for (OpenAI.ToolCall tc : msg.tool_calls) {
				JsonObject toolUse = new JsonObject();
				toolUse.addProperty("type", "tool_use");
				toolUse.addProperty("id", tc.id);
				toolUse.addProperty("name", tc.function.name);
				toolUse.add("input", JsonParser.parseString(tc.function.arguments));
				content.add(toolUse);
			}

			obj.add("content", content);
		}
		else {
			obj.addProperty("content", msg.content != null ? msg.content : "");
		}

		return(obj);
	}

	// +-------------------+
	// | convertToolResult |
	// +-------------------+

	private static JsonObject convertToolResult(OpenAI.Message msg) {

		JsonObject result = new JsonObject();
		result.addProperty("type", "tool_result");
		result.addProperty("tool_use_id", msg.tool_call_id);
		result.addProperty("content", msg.content != null ? msg.content : "");
		return(result);
	}

	// +----------------------+
	// | deserializeResponse  |
	// +----------------------+

	// Claude response:
	//   { id, model, stop_reason, content: [{type:"text",text:...}, {type:"tool_use",...}, {type:"thinking",...}],
	//     usage: { input_tokens, output_tokens } }
	//
	// Maps to OpenAI.Response with a single Choice

	public static ModelProvider.DeserializeResult deserializeResponse(String response, Gson gson) {

		ModelProvider.DeserializeResult result = new ModelProvider.DeserializeResult();
		
		JsonObject json = JsonParser.parseString(response).getAsJsonObject();

		result.Response.id = json.has("id") ? json.get("id").getAsString() : null;
		result.Response.model = json.has("model") ? json.get("model").getAsString() : null;

		// usage
		if (json.has("usage")) {
			JsonObject u = json.getAsJsonObject("usage");
			result.UsageDetails = u;
			result.Response.usage = new OpenAI.Usage();
			int input = u.has("input_tokens") ? u.get("input_tokens").getAsInt() : 0;
			int cacheWrite = u.has("cache_creation_input_tokens") ? u.get("cache_creation_input_tokens").getAsInt() : 0;
			int cacheRead = u.has("cache_read_input_tokens") ? u.get("cache_read_input_tokens").getAsInt() : 0;
			result.Response.usage.prompt_tokens = input + cacheWrite + cacheRead;
			result.Response.usage.completion_tokens = u.has("output_tokens") ? u.get("output_tokens").getAsInt() : 0;
			result.Response.usage.total_tokens = result.Response.usage.prompt_tokens + result.Response.usage.completion_tokens;
		}

		// stop_reason -> finish_reason
		String stopReason = json.has("stop_reason") ? json.get("stop_reason").getAsString() : "end_turn";
		String finishReason;
		switch (stopReason) {
			case "tool_use":    finishReason = OpenAI.FINISH_REASON_TOOLS; break;
			case "max_tokens":  finishReason = OpenAI.FINISH_REASON_TRUNC; break;
			default:            finishReason = OpenAI.FINISH_REASON_OK; break;
		}

		// content blocks -> message
		OpenAI.Message message = new OpenAI.Message();
		message.role = OpenAI.ROLE_ASST;

		StringBuilder textContent = new StringBuilder();
		StringBuilder thinkingContent = new StringBuilder();
		List<OpenAI.ToolCall> toolCalls = new ArrayList<OpenAI.ToolCall>();

		if (json.has("content")) {
			JsonArray content = json.getAsJsonArray("content");
			for (JsonElement el : content) {
				JsonObject block = el.getAsJsonObject();
				String type = block.get("type").getAsString();

				switch (type) {
					case "text":
						if (textContent.length() > 0) textContent.append("\n");
						textContent.append(block.get("text").getAsString());
						break;

					case "thinking":
						if (thinkingContent.length() > 0) thinkingContent.append("\n");
						thinkingContent.append(block.get("thinking").getAsString());
						break;

					case "tool_use":
						OpenAI.ToolCall tc = new OpenAI.ToolCall();
						tc.id = block.get("id").getAsString();
						tc.type = "function";
						tc.function = new OpenAI.FunctionCall();
						tc.function.name = block.get("name").getAsString();
						tc.function.arguments = gson.toJson(block.get("input"));
						toolCalls.add(tc);
						break;
				}
			}
		}

		message.content = textContent.length() > 0 ? textContent.toString() : null;
		message.reasoning_content = thinkingContent.length() > 0 ? thinkingContent.toString() : null;
		message.tool_calls = toolCalls.size() > 0 ? toolCalls : null;

		// build choice
		OpenAI.Choice choice = new OpenAI.Choice();
		choice.index = 0;
		choice.message = message;
		choice.finish_reason = finishReason;

		result.Response.choices = new OpenAI.Choice[] { choice };
		return(result);
	}

	// +-------------+
	// | convertTool |
	// +-------------+

	// OpenAI: { type: "function", function: { name, description, parameters } }
	// Claude: { name, description, input_schema }

	private static JsonObject convertTool(JsonObject openaiTool) {

		JsonObject fn = openaiTool.getAsJsonObject("function");

		JsonObject claudeTool = new JsonObject();
		claudeTool.addProperty("name", fn.get("name").getAsString());
		claudeTool.addProperty("description", fn.get("description").getAsString());
		claudeTool.add("input_schema", fn.get("parameters"));
		return(claudeTool);
	}
}
