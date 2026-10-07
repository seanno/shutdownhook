//
// MODELINFO.JAVA
//
// provider,url,display_name,id,max_input_tokens,max_tokens

package com.shutdownhook.colossus;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

public class ModelInfo
{
	public ModelProvider.Provider Provider;
	public String ApiUrl;
	public String DisplayName;
	public String ApiName;
	public long ContextSize = -1L;
	public long MaxOutputTokens = -1L;

	public static ModelInfo fromCSV(String csv) {
		String[] fields = csv.split(",");
		ModelInfo info = new ModelInfo();
		info.Provider = ModelProvider.Provider.valueOf(fields[0].trim().toUpperCase());
		info.ApiUrl = fields[1].trim();
		info.DisplayName = fields[2].trim();
		info.ApiName = fields[3].trim();
		info.ContextSize = Long.parseLong(fields[4].trim());
		info.MaxOutputTokens = Long.parseLong(fields[5].trim());
		return(info);
	}

	// +--------------+
	// | getModelInfo |
	// +--------------+

	public static ModelInfo getModelInfo(String modelName, String modelsCsvSmartyPath) throws Exception {
		
		ModelInfo info = loadModels(modelsCsvSmartyPath).get(modelName.toLowerCase());
		
		if (info == null) {
			String msg = "No model info found for: " + modelName;
			log.severe(msg);
			throw new Exception(msg);
		}
		
		return(info);
	}

	// +------------+
	// | loadModels |
	// +------------+

	private static Map<String,ModelInfo> loadModels(String smartyPath) throws Exception {

		String[] lines = Easy.stringFromSmartyPath(smartyPath).split("\n");

		Map<String,ModelInfo> models = new HashMap<String,ModelInfo>();
		
		for (String line: lines) {
			if (line.startsWith("provider,")) continue;
			ModelInfo info = ModelInfo.fromCSV(line);
			models.put(info.ApiName.toLowerCase(), info);
		}

		return(models);
	}

	protected final static Logger log = Logger.getLogger(ModelInfo.class.getName());
}
