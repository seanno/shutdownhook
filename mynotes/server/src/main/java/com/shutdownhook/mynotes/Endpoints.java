/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.mynotes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;

public class Endpoints
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String Source = "@endpoints.json";
		public Integer GramLength = 4;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Endpoints(Config cfg) throws Exception {
		this.cfg = cfg;
		loadGrams();
	}

	// +---------------+
	// | filter(Async) |
	// +---------------+

	public CompletableFuture<String> filterAsync(String input) {
		return(Exec.runAsync("filter", new Exec.AsyncOperation() {
			public String execute() throws Exception {
				return(filter(input));
			}
		}));
	}

	public String filter(String input) throws Exception {

		if (input.length() < cfg.GramLength) {
			log.warning(String.format("Can't filter on input less that %d chars", cfg.GramLength));
			return("[]");
		}

		String inputLower = input.toLowerCase();
		String gram = inputLower.substring(0, cfg.GramLength);

		if (!grams.containsKey(gram)) {
			return("[]");
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[");

		int count = 0;
		for (Endpoint e : grams.get(gram)) {
			if (e.LabelLower.indexOf(inputLower) == -1) continue;
			if (count > 0) sb.append(",");
			sb.append(e.Json);
			++count;
		}

		sb.append("]");
		return(sb.toString());
	}
	
	// +-----------+
	// | loadGrams |
	// +-----------+

	private static class Endpoint
	{
		public String LabelLower;
		public String Json;
	}

	private void loadGrams() throws Exception {

		log.info("Endpoings loading grams from " + cfg.Source);
		
		String json = Easy.stringFromSmartyPath(cfg.Source);
		JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

		this.grams = new HashMap<String,List<Endpoint>>();
		Map<String,Boolean> seen = new HashMap<String,Boolean>();

		for (int i = 0; i < arr.size(); ++i) {
			
			JsonObject obj = arr.get(i).getAsJsonObject();
			seen.clear();

			Endpoint e = new Endpoint();
			e.LabelLower = obj.get("label").getAsString().toLowerCase();
			e.Json = obj.toString();
			
			int cch = e.LabelLower.length();
			if (cch < cfg.GramLength) {
				log.warning(String.format("Skipping endpoint shorter than gram length (%s)", e.LabelLower));
				continue;
			}

			for (int j = 0; j < cch - cfg.GramLength + 1; ++j) {
				
				String gram = e.LabelLower.substring(j, j + cfg.GramLength);
				if (seen.containsKey(gram)) continue;

				if (!grams.containsKey(gram)) grams.put(gram, new ArrayList<Endpoint>());
				grams.get(gram).add(e);
				seen.put(gram, true);
			}
		}

		log.info(String.format("added %d %dgrams from %d endpoints",
							   grams.size(), cfg.GramLength, arr.size()));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private Map<String,List<Endpoint>> grams;

	private final static Logger log = Logger.getLogger(Endpoints.class.getName());
}
