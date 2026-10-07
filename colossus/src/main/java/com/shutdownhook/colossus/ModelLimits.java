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
	// +------------------------+
	// | ModelLimits (abstract) |
	// +------------------------+
	
    public ModelLimits(ModelInfo info, Utility utils) {
		this.info = info;
		this.utils = utils;
	}
		
	public abstract long getContextSize();
	public abstract long getInputTokenBudget(long maxOutputTokens);
	public abstract long countTokens(String input);

	protected ModelInfo info;
	protected Utility utils;

	private final static Logger log = Logger.getLogger(ModelLimits.class.getName());
	
	// +--------------------+
	// | ModelLimits.Simple |
	// +--------------------+

	public static class Simple extends ModelLimits
    {
		private static final String DEFAULT_MODELNAME = "Unknown";
	
		public static class Config
		{
		    public String ModelsCsvPath = "@models.csv";
			public double CharToTokenFactor = (1d / 3.0d); // conservative
		}
		
		public Simple(ModelInfo info, Utility utils, Config cfg) throws Exception {
			super(info, utils);
			this.cfg = cfg;
		}

		public long getContextSize() { return(info.ContextSize); }
		public long getInputTokenBudget(long maxOutputTokens) { return(info.ContextSize - maxOutputTokens); }
		public long countTokens(String input) { return((long)(((double)input.length()) * cfg.CharToTokenFactor)); }

		public String toString() { return(utils.getGson().toJson(info, ModelInfo.class)); }

		private Config cfg;
	}

	// +-------------------+
	// | ModelLimits.Llama |
	// +-------------------+

	public static class Llama extends ModelLimits
	{
		public static class Config
		{
			public String TokenizePath = "/tokenize";
			public String MinjaRenderPath = "~/.local/bin/minja_render";
			public String PropsPathPrefix = "/props?model=";
		}
		
		public Llama(ModelInfo info, Utility utils, Config cfg) throws Exception {
			super(info, utils);
			this.cfg = cfg;
			setupModelProps();
		}

		public long getContextSize() {	return(modelProps.default_generation_settings.n_ctx); }
		public long getInputTokenBudget(long maxOutputTokens) { return(getContextSize() - maxOutputTokens); }

		public String toString() { return(utils.getGson().toJson(modelProps)); }

		// +------------
		// | countTokens

		public long countTokens(String input) {

			String post = null;
		
			try {
				JsonObject jsonPost = new JsonObject();
				jsonPost.addProperty("model", info.ApiName);
				jsonPost.addProperty("content", input);

				post = jsonPost.toString();
				String url = Easy.urlPaste(info.ApiUrl, cfg.TokenizePath);
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
			String url = Easy.urlPaste(info.ApiUrl, cfg.PropsPathPrefix + Easy.urlEncode(info.ApiName));
			String body = utils.simpleFetchUrl(url, null);
			this.modelProps = utils.getGson().fromJson(body, ModelProps.class);
		}

		// +--------
		// | Members

		private Config cfg;
		private ModelProps modelProps;
	}

}

