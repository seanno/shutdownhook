/*
** MODELPROVIDER.JAVA
**
** Every problem is a normalization problem.
*/

package com.shutdownhook.colossus;

import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebRequests;

public abstract class ModelProvider
{
	public ModelProvider(Conversation conversation, ModelInfo info) {
		this.conversation = conversation;
		this.utils = conversation.getUtils();
		this.info = info;
	}

	public ModelInfo getInfo() { return(info); }
	
	public abstract ModelLimits getLimits();
	
	public long countTokens(OpenAI.Request request) throws Exception {
		return(getLimits().countTokens(serializeRequest(request)));
	}
	
	public abstract String serializeRequest(OpenAI.Request request) throws Exception;

	public static class DeserializeResult
	{
		public OpenAI.Response Response = new OpenAI.Response();
		public JsonObject UsageDetails;
	}
	
	public abstract DeserializeResult deserializeResponse(String response) throws Exception;

	public abstract String getCompletionPath();
	public abstract void addAuthenticationHeaders(WebRequests.Params params, String apiKey);

	protected Conversation conversation;
	protected Utility utils;
	protected ModelInfo info;
	
	protected final static Logger log = Logger.getLogger(ModelProvider.class.getName());

	// +----------------------+
	// | ModelProvider.create |
	// +----------------------+

	public static enum Provider
	{
		LLAMA,
		OPENAI,
		CLAUDE
	}
	
	public static ModelProvider create(Conversation conversation) throws Exception {
		
		Conversation.Config ccfg = conversation.getConfig();
		ModelInfo info = ccfg.ModelInfo;
		if (info == null || info.Provider == null) {
			info = ModelInfo.getModelInfo(ccfg.Model, ccfg.ModelsCsvPath);
		}
		
		switch (info.Provider) {

			case CLAUDE:
				return(new Provider_Claude(conversation, info));
				
			case LLAMA:
				return(new Provider_Llama(conversation, info));

			default:
			case OPENAI:
				return(new Provider_OpenAI(conversation, info));
		}
	}

	// +-----------------+
	// | Provider_OpenAI |
	// +-----------------+

	public static class Provider_OpenAI extends ModelProvider
	{
		public static class Config
		{
			public ModelLimits.Simple.Config Limits = new ModelLimits.Simple.Config();
		}
		
		public Provider_OpenAI(Conversation conversation, ModelInfo info) throws Exception {
			super(conversation, info);
			
			JsonObject cfgJson = conversation.getConfig().ModelProviderConfig;
			this.cfg = (cfgJson == null ? new Config() : utils.getGson().fromJson(cfgJson, Config.class));
						
			this.limits = new ModelLimits.Simple(info, utils, cfg.Limits);
		}
		
		public ModelLimits getLimits() { return(limits); }
		public String getCompletionPath() { return("/v1/chat/completions"); }

		public void addAuthenticationHeaders(WebRequests.Params params, String apiKey) {
			params.addHeader("Authorization", "Bearer " + apiKey);
		}

		public String serializeRequest(OpenAI.Request request) throws Exception {
			return(utils.getCompactGson().toJson(request));
		}
		
		public ModelProvider.DeserializeResult deserializeResponse(String response) throws Exception {
			
			DeserializeResult result = new DeserializeResult();
			JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
			result.Response = utils.getGson().fromJson(jsonResponse, OpenAI.Response.class);
			
			result.UsageDetails = new JsonObject();
			result.UsageDetails.add("usage", jsonResponse.get("usage"));
			result.UsageDetails.add("timings", jsonResponse.get("timings"));

			return(result);
		}

		
		private Config cfg;
		private ModelLimits.Simple limits;
	}
	
	// +----------------+
	// | Provider_Llama |
	// +----------------+

	public static class Provider_Llama extends Provider_OpenAI
	{
		public static class Config
		{
			public ModelLimits.Llama.Config Limits = new ModelLimits.Llama.Config();
		}
		
		public Provider_Llama(Conversation conversation, ModelInfo info) throws Exception {
			super(conversation, info);

			JsonObject cfgJson = conversation.getConfig().ModelProviderConfig;
			this.cfg = (cfgJson == null ? new Config() : utils.getGson().fromJson(cfgJson, Config.class));
			
			this.limits = new ModelLimits.Llama(info, utils, cfg.Limits);
		}
		
		public ModelLimits getLimits() { return(limits); }

		private Config cfg;
		private ModelLimits.Llama limits;
	}
	
	// +-----------------+
	// | Provider_Claude |
	// +-----------------+

	public static class Provider_Claude extends ModelProvider
	{
		public static class Config
		{
			public boolean AutomaticCaching = true;
			public ModelLimits.Simple.Config Limits = new ModelLimits.Simple.Config();
		}
		
		public Provider_Claude(Conversation conversation, ModelInfo info) throws Exception {
			super(conversation, info);

			JsonObject cfgJson = conversation.getConfig().ModelProviderConfig;
			this.cfg = (cfgJson == null ? new Config() : utils.getGson().fromJson(cfgJson, Config.class));
			
			this.limits = new ModelLimits.Simple(info, utils, cfg.Limits);
		}
		
		public String serializeRequest(OpenAI.Request request) throws Exception {
			return(Claude.serializeRequest(request, cfg.AutomaticCaching, utils.getCompactGson()));
		}
		
		public DeserializeResult deserializeResponse(String response) throws Exception {
			return(Claude.deserializeResponse(response, utils.getGson()));
		}
		
		public ModelLimits getLimits() { return(limits); }
		public String getCompletionPath() { return("/v1/messages"); }
		
		public void addAuthenticationHeaders(WebRequests.Params params, String apiKey) {
			params.addHeader("x-api-key", apiKey);
			params.addHeader("anthropic-version", "2023-06-01");
		}

		private Config cfg;
		private ModelLimits.Simple limits;
	}

}

