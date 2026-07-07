//
// CODESANDBOX.JAVA
//

package com.shutdownhook.colossus;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;

public class CodeSandbox 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public static String BasePath = null;
		
		public static String Memory = "1g";
		public static String TempSize = "64m";
		public static String Timeout = "30s";

		public static String ImageName = "code-sandbox";

		public static String DockerCommandTemplate =
			"timeout {{TIMEOUT}} docker run --rm " +
			"--network none --read-only " +
			"--memory {{MEM}} --memory-swap {{MEM}} " +
			"--cpus 1 --tmpfs /tmp:size={{TEMP}} " +
			"{{:if HAVE_BASE_PATH}} -v '{{BASE_PATH}}:/data' --workdir '/data' {{:end}} " +
			"{{IMAGE}} /bin/sh -c \"echo {{:raw CMD}} | base64 -d | /bin/sh\" ";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}

		public String toJson() {
			return(new Gson().toJson(this));
		}

	}

	public CodeSandbox(Config cfg) throws Exception {
		this.cfg = cfg;
		this.tmpl = new Template(cfg.DockerCommandTemplate);
	}

	// +-----+
	// | run |
	// +-----+

	public String run(String command) throws Exception {

		Map<String,String> params = new HashMap<String,String>();
		params.put("TIMEOUT", cfg.Timeout);
		params.put("MEM", cfg.Memory);
		params.put("TEMP", cfg.TempSize);
		params.put("IMAGE", cfg.ImageName);
		params.put("CMD", Easy.base64Encode(command)); // NOTE this avoids escaping issues!

		params.put("HAVE_BASE_PATH", cfg.BasePath == null ? "false" : "true");
		params.put("BASE_PATH", cfg.BasePath);

		String dockerCommand = tmpl.render(params);
		return(Easy.stringFromProcess(dockerCommand, true));
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Template tmpl;
	
	private final static Logger log = Logger.getLogger(CodeSandbox.class.getName());
}
