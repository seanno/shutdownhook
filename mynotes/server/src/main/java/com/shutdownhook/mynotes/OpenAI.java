/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.mynotes;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public class OpenAI implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Token;
		public String SystemPrompt;

		public String Model = "gpt-4o"; 
		public double Temperature = 0.2;

		public String Url = "https://api.openai.com/v1/chat/completions";
		
		public WebRequests.Config WebRequests = new WebRequests.Config();

		public static Config fromJson(String json) {
			Config cfg = gson.fromJson(json, Config.class);
			return(cfg);
		}
	}
	
	public OpenAI(Config cfg) throws Exception {
		this.cfg = cfg;
		this.requests = new WebRequests(cfg.WebRequests);
	}
	
	public void close() {
		requests.close();
	}
	
	// +---------+
	// | explain |
	// +---------+

	// note no explicitly async model because the web request already
	// uses async i/o ... maybe a bit lazy but it'll be fine.

	public String explain(String input) throws Exception {

		ChatRequest chatRequest = new ChatRequest();
		chatRequest.model = cfg.Model;
		chatRequest.temperature = cfg.Temperature;
		chatRequest.n = 1;

		Message msg = new Message();
		chatRequest.messages.add(msg);
		msg.role = SYSTEM_ROLE;
		msg.content = cfg.SystemPrompt;;
		
		msg = new Message();
		chatRequest.messages.add(msg);
		msg.role = USER_ROLE;
		msg.content = input;

		WebRequests.Params params = new WebRequests.Params();
		params.Body = gson.toJson(chatRequest);
		params.setContentType("application/json");
		params.addHeader("Authorization", "Bearer " + cfg.Token);

		WebRequests.Response response = requests.fetch(cfg.Url, params);
		if (!response.successful()) response.throwException("explain");

		ChatResponse chatResponse = gson.fromJson(response.Body, ChatResponse.class);

		if (chatResponse.choices == null || chatResponse.choices.length == 0) {
			
			log.warning(String.format("No response from openai:\n%s\n%s",
									  params.Body, response.Body));
			
			throw new Exception("no response content from openai");
		}

		return(chatResponse.choices[0].message.content);
	}

	// +--------+
	// | Models |
	// +--------+

	public static class Message
	{
		public String role;
		public String content;
	}
	
	public static class ChatRequest
	{
		public String model;
		public List<Message> messages = new ArrayList<Message>();

		public double temperature;
		public int n;
	}

	public static class ChatResponse
	{
		public String id;
		public Choice[] choices;
		public Usage usage;
		
		public static class Choice
		{
			public int index;
			public Message message;
			public String finish_reason;
		}

		public static class Usage
		{
			public int prompt_tokens;
			public int completion_tokens;
			public int total_tokens;
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebRequests requests;

	private final static Logger log = Logger.getLogger(OpenAI.class.getName());
	private final static Gson gson = new Gson();

	private final static String SYSTEM_ROLE = "system";
	private final static String USER_ROLE = "user";
}
