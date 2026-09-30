/*
** MODELLIMITS.JAVA
**
** Various ways to stay within token budgets
*/

package com.shutdownhook.colossus;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;

public abstract class ModelLimits
{
	public ModelLimits(Conversation conversation) {
		this.conversation = conversation;
		this.utils = conversation.getUtils();
	}
		
	public abstract long getContextSize();
	public abstract long getInputTokenBudget(long maxOutputTokens);
	public abstract long countTokens(OpenAI.Request request);
	public abstract long countTokens(String input);

	public String toString() { return("na"); }

	protected Conversation conversation;
	protected Utility utils;
	
	// +-------------------+
	// | createModelLimits |
	// +-------------------+

	public static ModelLimits createModelLimits(Conversation conversation) throws Exception {

		String modelProvider = conversation.getConfig().ModelProvider;
		if (Easy.nullOrEmpty(modelProvider)) modelProvider = Conversation.MODEL_PROVIDER_LLAMA;
		
		switch (modelProvider) {
			
			case Conversation.MODEL_PROVIDER_LLAMA:
				return(new ModelLimits_Llama(conversation));

			case Conversation.MODEL_PROVIDER_OTHER:
			default:
				return(new ModelLimits_Simple(conversation));
		}
	}
	
	// +--------------------+
	// | ModelLimits_Simple |
	// +--------------------+

	public static class ModelLimits_Simple extends ModelLimits
	{
		public static class Config
		{
			public String ModelsCsvPath = "@models.csv";
			public double CharToTokenFactor = (1d / 3.0d); // conservative

			public long DefaultContextSize = 132000;
		}
		
		public ModelLimits_Simple(Conversation conversation) throws Exception {
			super(conversation);
			this.cfg = getConfig();
			setContextSize();
		}

		private Config getConfig() {
			JsonObject providerConfig = conversation.getConfig().ModelProviderConfig;
			if (providerConfig == null) return(new Config());
			return(utils.getGson().fromJson(providerConfig.toString(), Config.class));
		}

		private void setContextSize() throws Exception {
			
			Map<String,ModelInfo> models = ModelInfo.loadModels(cfg.ModelsCsvPath);

			String modelName = conversation.getConfig().Model;
			ModelInfo info = models.get(modelName.toLowerCase());

			if (info == null) {
				this.contextSize = cfg.DefaultContextSize;
				log.warning(String.format("Could not find info for model %s; using default %d",
										  modelName, cfg.DefaultContextSize));
			}
			else {
				this.contextSize = info.ContextSize;
			}
		}

		public long getContextSize() { return(contextSize); }
		public long getInputTokenBudget(long maxOutputTokens) { return(contextSize - maxOutputTokens); }
		public long countTokens(OpenAI.Request request) { return(countTokens(request.toString())); }
		public long countTokens(String input) { return((long)(((double)input.length()) * cfg.CharToTokenFactor)); }

		public String toString() { return(String.format("{ \"contextSize\": %d }", contextSize)); }

		private Config cfg;
		private long contextSize;
	}

	// +-------------------+
	// | ModelLimits_Llama |
	// +-------------------+

	public static class ModelLimits_Llama extends ModelLimits
	{
		public static class Config
		{
			public String TokenizePath = "/tokenize";
			public String MinjaRenderPath = "~/.local/bin/minja_render";
			public String PropsPathPrefix = "/props?model=";
		}
		
		public ModelLimits_Llama(Conversation conversation) throws Exception {
			super(conversation);
			this.cfg = getConfig();
			setupModelProps();
		}

		private Config getConfig() {
			JsonObject providerConfig = conversation.getConfig().ModelProviderConfig;
			if (providerConfig == null) return(new Config());
			return(utils.getGson().fromJson(providerConfig.toString(), Config.class));
		}

		public long getContextSize() {	return(modelProps.default_generation_settings.n_ctx); }
		public long getInputTokenBudget(long maxOutputTokens) { return(getContextSize() - maxOutputTokens); }

		public String toString() { return(utils.getGson().toJson(modelProps)); }

		// +------------
		// | countTokens

		public long countTokens(OpenAI.Request request) {
			String templated = applyModelTemplate(request);
			if (templated == null) return(0L); // degenerate case
			return(countTokens(templated));
		}
		
		public long countTokens(String input) {

			String post = null;
		
			try {
				JsonObject jsonPost = new JsonObject();
				jsonPost.addProperty("model", conversation.getConfig().Model);
				jsonPost.addProperty("content", input);

				post = jsonPost.toString();
				String url = Easy.urlPaste(conversation.getConfig().BaseUrl, cfg.TokenizePath);
				String body = utils.simpleFetchUrl(url, post);
				JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();

				return(jsonResponse.get("tokens").getAsJsonArray().size());
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "getTokenCount", true));
				return(0L);
			}
		}

		// +-------------------
		// | applyModelTemplate

		// uses a callout to minja-render (cfg.MiniJinjaPath) to render a request into
		// a chat template so we can accurately measure its token count via getTokenCount().
		// note minja-render is buildable from shutdownhook/colossus/minja-render, you'll
		// need C++ and CMake.
	
		private String applyModelTemplate(OpenAI.Request req) {

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

		// +-----------
		// | ModelProps
		
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

		private final static int MODEL_PROPS_DELAY_MS = 2000;
		private final static int MODEL_PROPS_RETRY_LIMIT = 3;
	
		private void setupModelProps() throws Exception {

			int tries = 0;
		
			while (true) {
			
				try {
					++tries;
					trySetupModelProps();
					return;
				}
				catch (Exception e) {
				
					if (tries < MODEL_PROPS_RETRY_LIMIT) {
						log.info("(Hopefully) transient ex getting model props; will retry: " + e.toString());
						Thread.sleep(MODEL_PROPS_DELAY_MS);
					}
					else {
						log.severe("FATAL ex getting model props; will retry: " + e.toString());
						return;
					}
				}
			}
		}

		private void trySetupModelProps() throws Exception {
			Conversation.Config convoCfg = conversation.getConfig();
			String url = Easy.urlPaste(convoCfg.BaseUrl, cfg.PropsPathPrefix + Easy.urlEncode(convoCfg.Model));
			String body = utils.simpleFetchUrl(url, null);
			this.modelProps = utils.getGson().fromJson(body, ModelProps.class);
		}

		// +--------
		// | Members

		private Config cfg;
		private ModelProps modelProps;
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(ModelLimits.class.getName());
}

