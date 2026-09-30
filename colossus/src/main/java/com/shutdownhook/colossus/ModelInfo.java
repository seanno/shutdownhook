//
// MODELINFO.JAVA
//
// provider,url,display_name,id,max_input_tokens,max_tokens

package com.shutdownhook.colossus;

import java.util.HashMap;
import java.util.Map;

import com.shutdownhook.toolbox.Easy;

public class ModelInfo
{
	public String Provider;
	public String ApiUrl;
	public String DisplayName;
	public String ApiName;
	public long ContextSize;
	public long MaxOutputTokens;

	public static ModelInfo fromCSV(String csv) {
		String[] fields = csv.split(",");
		ModelInfo info = new ModelInfo();
		info.Provider = fields[0].trim();
		info.ApiUrl = fields[1].trim();
		info.DisplayName = fields[2].trim();
		info.ApiName = fields[3].trim();
		info.ContextSize = Long.parseLong(fields[4].trim());
		info.MaxOutputTokens = Long.parseLong(fields[5].trim());
		return(info);
	}

	// +------------+
	// | loadModels |
	// +------------+

	public static Map<String,ModelInfo> loadModels(String smartyPath) throws Exception {

		String[] lines = Easy.stringFromSmartyPath(smartyPath).split("\n");

		Map<String,ModelInfo> models = new HashMap<String,ModelInfo>();
		
		for (String line: lines) {
			if (line.startsWith("provider,")) continue;
			ModelInfo info = ModelInfo.fromCSV(line);
			models.put(info.ApiName.toLowerCase(), info);
		}

		return(models);
	}
}
