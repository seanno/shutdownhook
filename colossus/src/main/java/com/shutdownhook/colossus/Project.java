//
// PROJECT.JAVA
//

package com.shutdownhook.colossus;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.colossus.ToolCalling.ToolClass;

public class Project
{
	// +-----------------+
	// | GlobalUtilities |
	// +------------------+

	public static void initGlobalUtilities(String path) throws Exception {
		if (utils != null) throw new Exception("Project Global Utilities double-initialized");

		Path cfgPath = Paths.get(path, GLOBAL_UTILITIES);
		if (!Files.exists(cfgPath)) {
			utils = new Utility(new Utility.Config());
			return;
		}
		
		String json = Easy.stringFromFile(cfgPath.toAbsolutePath().toString());
		utils = new Utility(Utility.Config.fromJson(json));
	}

	public static void closeGlobalUtilities() {
		if (utils == null) return;
		utils.close();
		utils = null;
	}

	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public Project(String path, Conversation.Config parentCfg) throws Exception {

		if (utils == null) log.warning("Warning; Global Utilities not initialized");
		
		this.projectPath = Paths.get(path).toAbsolutePath();
		this.parentCfg = parentCfg;
		
		setupConversationConfig();
	}

	// +-----+
	// | run |
	// +-----+

	public static class ProjectResult
	{
		public String Name;
		public String Response;
		public Exception Ex;
		public String Started;
		public String Finished;

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("# ").append(Name).append("\n");
			sb.append("Started:\t").append(Started).append("\n");
			sb.append("Finished:\t").append(Finished).append("\n");
			sb.append(Response == null ? "[No Response]" : Response).append("\n");
			if (Ex != null) sb.append(Ex).append("\n");
			sb.append("\n");
			return(sb.toString());
		}
	}

	public boolean run(List<ProjectResult> results) {
		return(run(results, null, null, null));
	}

	public boolean run(List<ProjectResult> results, String parentName, String
					   targetProject, String promptOverride) {

		String projectName = (parentName == null ? "" : parentName + " : ") + projectPath.getFileName();
		if (shouldSkip(projectName, targetProject)) return(true);

		// two ways to pause ... the rename causes havoc with my onedrive sync so
		// added the file method as well.
		if (projectName.toLowerCase().endsWith(PAUSED_SUFFIX)) return(true);
		try { if (Files.exists(getProjectFile(PAUSED_FILE))) return(true); }
		catch (Exception ePause) { /* eat it I guess, things will fail in a second anyways */ }
			
		ProjectResult result = new ProjectResult();
		results.add(result);
		
		result.Name = projectName;
		result.Started = Instant.now().toString();
		
		log.info(">>>>> STARTED project: " + projectPath.toString());
		Instant started = Instant.now();
		
		Conversation conversation = null;
		Path archiveDir = null; 
		
		try {
			// (1) prework
			ensureDataAndClearTemp();
			runScript(PRE_SCRIPT_FILE);

			Path quickExitFile = getProjectSubDirectory(DATA_DIR, TEMP_SUBDIR).resolve(QUICK_EXIT_FILE);
			if (Files.exists(quickExitFile)) {
				// if pre script writes this file into the (newly-cleared) temp
				// directory, don't do anything else --- including the post
				String msg = "";
				try { msg = Easy.stringFromFile(quickExitFile.toString()); }
				catch (Exception eq) { /* eat it */ }
				result.Response = "[Quick exit] " + msg;
			}
			else {
				// (2) children
				Path children = getProjectDirectory(CHILDREN_DIR, false);
				if (Files.exists(children)) {
					for (Path childPath : Files.list(children).toList()) {
						Project childProject = new Project(childPath.toString(), thisCfg);
						childProject.run(results, result.Name, targetProject, promptOverride);
					}
				}

				// (3) conversation
				String effectivePrompt = null;
				boolean override = false;
			
				if (projectName.equals(targetProject) && promptOverride != null) {
					// override
					effectivePrompt = promptOverride;
					override = true;
				}
				else {
					Path promptPath = getProjectFile(PROMPT_FILE);
					if (Files.exists(promptPath)) {
						// explicit prompt
						effectivePrompt = Easy.stringFromFile(promptPath.toString());
					}
					else if (!Files.exists(children) && !Easy.nullOrEmpty(thisCfg.SystemPrompt)) {
						// implicit prompt at leaf (only if we have some prompt to give)
						effectivePrompt = START_PROMPT;
					}
				}

				if (effectivePrompt != null) {
					archiveDir = getProjectDirectory(CONVERSATIONS_DIR);
					conversation = new Conversation(thisCfg);
					result.Response = conversation.safePrompt(effectivePrompt);
					if (!override && Easy.nullOrEmpty(result.Response)) {
						String wrapUpPrompt = getWrapUpPrompt(conversation);
						if (wrapUpPrompt != null) result.Response = conversation.safePrompt(wrapUpPrompt);
					}
				}

				// (4) postwork
				runScript(POST_SCRIPT_FILE);
				//ensureDataAndClearTemp(); // don't do this, for debugging purposes
			}

			if (result.Response == null) result.Response = "OK";
			
			log.info(String.format("<<<<< :) FINISHED project in %s: %s",
								   Duration.between(started, Instant.now()),
								   projectPath));

			return(true);
		}
		catch (Exception e) {

			log.severe(Easy.exMsg(e, "runProject", true));
			log.severe(String.format("<<<<< :( FAILED project in %s: %s",
								   Duration.between(started, Instant.now()),
								   projectPath));
			
			result.Ex = e;
			return(false);
		}
		finally {
			result.Finished = Instant.now().toString();
			if (archiveDir != null && conversation != null) conversation.archive(archiveDir);
			archiveRun(result);;
			Easy.safeClose(conversation);
		}
	}

	private boolean shouldSkip(String thisProject, String targetProject) {
		if (targetProject == null) return(false);
		if (thisProject.startsWith(targetProject)) return(false); // descendant
		if (targetProject.startsWith(thisProject)) return(false); // ancestor
		return(true);
	}
	
	// +------------+
	// | archiveRun |
	// +------------+

	private void archiveRun(ProjectResult result) {
		try {
			Path runFile = getProjectDirectory(DATA_DIR).resolve(RUNS_FILE);
			Easy.appendStringToFile(runFile.toString(), result.toString());
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "archiveRun", true));
		}
	}

	// +-------------------------+
	// | setupConversationConfig |
	// +-------------------------+

	private void setupConversationConfig() throws Exception {

		// 1. start from convo config from here, or from parent with optional overrides
		
		Path projectCfgPath = getProjectFile(CONVERSATION_CONFIG_FILE);

		if (Files.exists(projectCfgPath)) {
			if (parentCfg == null) {
				thisCfg = Conversation.Config.fromJson(Easy.stringFromFile(projectCfgPath.toString()));
			}
			else {
				thisCfg = parentCfg.override(projectCfgPath);
			}
		}
		else {
			thisCfg = ((parentCfg == null ? new Conversation.Config() : parentCfg.clone()));
		}

		// 2. inherit the system prompt

		Path projectSys = getProjectFile(SYSTEMPROMPT_FILE);
		
		if (Files.exists(projectSys)) {
			thisCfg.SystemPrompt =
				(thisCfg.SystemPrompt == null ? "" : thisCfg.SystemPrompt + "\n") +
				Easy.stringFromFile(projectSys.toString());
		}

		// 3. force the working directory for file and code tools if present

		List<ToolClass> thisTools = new ArrayList<ToolClass>();

		String superToolClass = ToolCalling.Super_Tool.class.getName();
		String scriptToolClass = Script_Tool.class.getName();
		
		String scriptsDir = getProjectDirectory(SCRIPTS_DIR).toString();
		String dataDir = getProjectDirectory(DATA_DIR).toString();

		if (thisCfg.ToolClasses != null) {
			
			for (ToolClass toolClass : thisCfg.ToolClasses) {
				
				if (toolClass == null) continue; // defense against stray trailing commas in config
				
				if (superToolClass.equals(toolClass.ClassName)) {
					if (toolClass.Config == null) toolClass.Config = new JsonObject();
					toolClass.Config.addProperty("BasePath", dataDir);
				}
				else if (scriptToolClass.equals(toolClass.ClassName)) {
					if (toolClass.Config == null) toolClass.Config = new JsonObject();
					toolClass.Config.addProperty("ScriptsDirectory", scriptsDir);
					toolClass.Config.addProperty("DataDirectory", dataDir);
				}

				thisTools.add(toolClass);
			}
		}

		thisCfg.ToolClasses = thisTools.toArray(new ToolClass[thisTools.size()]);
	}

	// +-----------+
	// | runScript |
	// +-----------+

	private boolean runScript(String script) throws Exception {
		
		Path scriptsDir = getProjectDirectory(SCRIPTS_DIR, false).toAbsolutePath();
		Path dataDir = getProjectDirectory(DATA_DIR);

		Path scriptFile = scriptsDir.resolve(script);
		if (!Files.exists(scriptFile)) return(true);

		Utility.ProcessResult result = runScript(scriptFile.toString(), scriptsDir, dataDir);
		return(result.ExitCode == 0);
	}

	private static Utility.ProcessResult runScript(String script, Path scriptsDir, Path dataDir) throws Exception {

		log.info(String.format("RUNNING %s with s=%s d=%s", script, scriptsDir, dataDir));
		
		String jarPath = getJarPath();
		String secretsVolume = getSecretsVolume();
		String preamble = String.format(SCRIPT_PREAMBLE_FMT, secretsVolume, jarPath);

		Utility.ProcessOptions options = new Utility.ProcessOptions();
		options.CaptureErrorStream = true;
		options.WorkingDirectory = scriptsDir.toString();
		options.Environment.put(DATA_DIR_ENV, dataDir.toString());
		options.Environment.put(JAR_PATH_ENV, jarPath);

		return(utils.runProcess(preamble + script, options));
	}

	private static String getJarPath() throws Exception {
		return(new File(Project.class
						.getProtectionDomain()
						.getCodeSource()
						.getLocation()
						.toURI()).getAbsolutePath());
	}

	private static String getSecretsVolume() throws Exception {
		
		String path = Easy.resolvePathFully(SCRIPT_PREAMBLE_SECRETS_FILE);
		if (!Files.exists(Paths.get(path))) {
			log.warning("No ~/.colossus-env file found; can't use with-secrets");
			return("");
		}
		
		return(String.format(SCRIPT_PREAMBLE_SECRETS_VOLUME_FMT, path));
	}
	
	// +------------------------+
	// | ensureDataAndClearTemp |
	// +------------------------+

	private void ensureDataAndClearTemp() throws Exception {

		// create data if needed
		Path dataPath = getProjectDirectory(DATA_DIR);

		// ensure temp and clear it out if needed
		Path tempPath = getProjectSubDirectory(DATA_DIR, TEMP_SUBDIR);
		if (Files.exists(tempPath)) { Easy.recursiveDelete(tempPath.toFile(), false); }
		else { Files.createDirectory(tempPath); }

		// symlink to parent data
		if (parentCfg != null) {
			Path parentPathLink = dataPath.resolve(PARENT_DATA_NAME);
			if (!Files.exists(parentPathLink)) {
				Path parentPathTarget = projectPath.resolve(PARENT_DATA_DIR);
				log.info(String.format("Creating parent symlink: L=%s, T=%s", parentPathLink, parentPathTarget));
				Files.createSymbolicLink(parentPathLink, parentPathTarget);
			}
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	private Path getProjectFile(String file) throws Exception {
		return(projectPath.resolve(file));
	}

	private Path getProjectDirectory(String dir, boolean createIfNeeded) throws Exception {
		Path path = projectPath.resolve(dir);
		if (!Files.exists(path) && createIfNeeded) Files.createDirectory(path);
		return(path);
	}

	private Path getProjectDirectory(String dir) throws Exception {
		return(getProjectDirectory(dir, true));
	}

	private Path getProjectSubDirectory(String dir, String subdir) throws Exception {
		Path path = getProjectDirectory(dir).resolve(subdir);
		if (!Files.exists(path)) Files.createDirectory(path);
		return(path);
	}

	private String getWrapUpPrompt(Conversation conversation) {

		String prompt = WRAPUP_PROMPT_PREFIX;
		
		String reasoning = conversation.getLastReasoning();
		if (!Easy.nullOrEmpty(reasoning)) {
			if (reasoning.length() > WRAPUP_REASONING_CCH_MAX) {
				reasoning = reasoning.substring(0, WRAPUP_REASONING_CCH_MAX) + "...";
			}

			prompt += WRAPUP_PROMPT_REASONING + reasoning;
		}

		return(prompt);
	}

	// +-------------+
	// | Script_Tool |
	// +-------------+

	// runs a script and returns stdout/stderr as a string.
	// PWD is the scripts directory, $DATA_DIR will be set just like
	// for pre and post scripts. If arguments is non-null and non-empty,
	// it will be written to a temp file and passed as a final parameter
	// tacked onto the end of CommandLine.

	public static class Script_Tool implements ToolCalling.Tool
	{
		public static class Config
		{
			public String CommandLine;
			public String ScriptsDirectory;
			public String DataDirectory;
		}

		public JsonObject initialize(ToolCalling.ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, Config.class);
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@script_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {

			String cmd = cfg.CommandLine;

			String paramsJSON = ToolCalling.getStringField(arguments, "params_json");
			if (!Easy.nullOrEmpty(paramsJSON) && !paramsJSON.replaceAll("\\s", "").equals("{}")) {
				Path tempPath = Paths.get(cfg.DataDirectory, TEMP_SUBDIR);
				String paramsFile = Files.createTempFile(tempPath, null, ".json").toString();
				Easy.stringToFile(paramsFile, paramsJSON);
				cmd = String.format("%s \"%s\"", cmd, paramsFile);
			}

			Path scriptsDir = Paths.get(cfg.ScriptsDirectory);
			Path dataDir = Paths.get(cfg.DataDirectory);

			if (!Files.exists(scriptsDir) || !Files.exists(dataDir)) {
				throw new Exception("Required directories missing");
			}
			
			Utility.ProcessResult result = runScript(cmd, scriptsDir, dataDir);
			return((result.ExitCode == 0 ? "" : "ERROR\n") + result.Output);
		}

		private Config cfg;
 	}

	// +-----------+
	// | Constants |
	// +-----------+

	private final static String PRE_SCRIPT_FILE = "pre.sh"; // in scripts dir
	private final static String POST_SCRIPT_FILE = "post.sh"; // in scripts dir
	private final static String CONVERSATION_CONFIG_FILE = "conversation.json";
	private final static String SYSTEMPROMPT_FILE = "systemprompt.md";
	private final static String PROMPT_FILE = "prompt.md";
	private final static String LEARNINGS_FILE = "learnings.json";
	private final static String RUNS_FILE = "runs.md";
	private final static String PAUSED_FILE = "paused.txt";
	private final static String QUICK_EXIT_FILE = "exit.txt";

	private final static String DATA_DIR = "data";
	private final static String TEMP_SUBDIR = "temp";
	
	private final static String PARENT_DATA_DIR = "../../data";
	private final static String PARENT_DATA_NAME = "parent";
	
	private final static String CHILDREN_DIR = "children";
	private final static String CONVERSATIONS_DIR = "conversations";
	private final static String SCRIPTS_DIR = "scripts";

	private final static String DATA_DIR_ENV = "DATA_DIR";
	private final static String JAR_PATH_ENV = "COLOSSUS_JAR_PATH";

	private final static String SCRIPT_PREAMBLE_FMT =
		"colossus_util() { docker run --rm --user \"$(id -u):$(id -g)\" " +
		"%s -v \"$DATA_DIR\":/data -v \"$PWD\":/scripts:ro colossus-utils \"$@\"; } \n" +
		"colossus_jar() { java -cp %s \"$@\"; } \n" +
		"export -f colossus_util; export -f colossus_jar; \n";

	private final static String SCRIPT_PREAMBLE_SECRETS_FILE = 
		"~/.colossus-env";

	private final static String SCRIPT_PREAMBLE_SECRETS_VOLUME_FMT =
		"-v \"%s\":/secrets.env:ro";
	
	private final static int PROCESS_TIMEOUT_SECONDS = 60 * 20; // 20 minutes

	private final static String PAUSED_SUFFIX = ".paused";

	private final static String WRAPUP_PROMPT_PREFIX = 
		"Your previous response was empty. Give a non-empty response.";

	private final static String WRAPUP_PROMPT_REASONING =
		" Continue from this reasoning: ";

	private final static int WRAPUP_REASONING_CCH_MAX = 200;

	private final static String START_PROMPT = "Begin";

	private final static String GLOBAL_UTILITIES = "globals.json";
	
	// +---------+
	// | Members |
	// +---------+

	private Path projectPath;
	private Conversation.Config parentCfg;
	private Conversation.Config thisCfg;

	private static Utility utils;
	
	private final static Logger log = Logger.getLogger(Project.class.getName());
}
