//
// OPENAI.JAVA
// OpenAI wire formats
//

package com.shutdownhook.colossus;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class OpenAI
{
	public static final String ROLE_USER = "user";
	public static final String ROLE_ASST = "assistant";
	public static final String ROLE_TOOL = "tool";
	public static final String ROLE_SYSTEM = "system";

	public static final String FINISH_REASON_OK = "stop";
	public static final String FINISH_REASON_TOOLS = "tool_calls";
	public static final String FINISH_REASON_TRUNC = "length";
	public static final String FINISH_REASON_FILTER = "content_filter";

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
		public String reasoning_content;
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
		public String reasoning_effort;

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
}
