//
// PROJECT.JAVA
//

package com.shutdownhook.colossus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Template;
import com.shutdownhook.colossus.ToolCalling.ToolClass;

public class Project
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public Project(String path, Conversation.Config parentCfg, String parentPrompt) throws Exception {
		this.projectPath = Paths.get(path);
		this.parentCfg = parentCfg;
		this.parentPrompt = parentPrompt;
		
		setupConversationConfig();
		setupConversationPrompt();
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
		if (targetProject != null && !targetProject.startsWith(projectName)) return(true);

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
			// prework
			ensureDataAndClearTemp();
			runScript(PRE_SCRIPT_FILE);

			Path children = getProjectDirectory(CHILDREN_DIR, false);
			if (Files.exists(children)) {
				// child projects
				for (Path childPath : Files.list(children).toList()) {
					Project childProject = new Project(childPath.toString(), thisCfg, thisPrompt);
					childProject.run(results, result.Name, targetProject, promptOverride);
				}
			}
			else {
				String effectivePrompt = thisPrompt;
				boolean override = false;
				if (projectName.equals(targetProject) && promptOverride != null) {
					effectivePrompt = promptOverride;
					override = true;
				}

				if (effectivePrompt != null) {
					// project conversation
					archiveDir = getProjectDirectory(CONVERSATIONS_DIR);
					conversation = new Conversation(thisCfg);
					result.Response = conversation.safePrompt(effectivePrompt);
					if (!override && Easy.nullOrEmpty(result.Response)) {
						String wrapUpPrompt = getWrapUpPrompt();
						if (wrapUpPrompt != null) result.Response = conversation.safePrompt(wrapUpPrompt);
					}
				}
			}

			// postwork
			runScript(POST_SCRIPT_FILE);
			ensureDataAndClearTemp();

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
	// | setupConversationPrompt |
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
		String dataDir = getProjectDirectory(DATA_DIR).toString();

		if (thisCfg.ToolClasses != null) {
			
			for (ToolClass toolClass : thisCfg.ToolClasses) {
				
				if (toolClass == null) continue; // defense against stray trailing commas in config
				
				if (superToolClass.equals(toolClass.ClassName)) {
					if (toolClass.Config == null) toolClass.Config = new JsonObject();
					toolClass.Config.addProperty("BasePath", dataDir);
				}

				thisTools.add(toolClass);
			}
		}

		thisCfg.ToolClasses = thisTools.toArray(new ToolClass[thisTools.size()]);
	}

	private void setupConversationPrompt() throws Exception {

		thisPrompt = parentPrompt;
		
		Path prompt = getProjectFile(PROMPT_FILE);
		if (Files.exists(prompt)) {
			String newPrompt = Easy.stringFromFile(prompt.toString());
			thisPrompt = (thisPrompt == null ? newPrompt : thisPrompt + "\n" + newPrompt);
		}
	}

	// +-----------+
	// | runScript |
	// +-----------+

	private boolean runScript(String script) throws Exception {
		
		Path scriptsDir = getProjectDirectory(SCRIPTS_DIR, false).toAbsolutePath();
		if (!Files.exists(scriptsDir)) return(true);
		Path scriptFile = scriptsDir.resolve(script).toAbsolutePath();
		if (!Files.exists(scriptFile)) return(true);

		String[] commands = new String[] { "bash", "-c", scriptFile.toString() };
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.directory(scriptsDir.toFile());
		pb.environment().put(DATA_DIR_ENV, getProjectDirectory(DATA_DIR).toString());

		log.info("Running script " + scriptFile.toString());
		
		Process p = pb.start();
		p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		int exit = p.exitValue();
		if (exit != 0) log.warning(String.format("Error %d running script %s", exit, scriptFile.toString()));
		
		return(exit == 0);
	}
							  
	// +------------------------+
	// | ensureDataAndClearTemp |
	// +------------------------+

	private void ensureDataAndClearTemp() throws Exception {

		// this by side-effect creates DATA_DIR if needed
		Path tempPath = getProjectSubDirectory(DATA_DIR, TEMP_SUBDIR);
		
		if (Files.exists(tempPath)) { Easy.recursiveDelete(tempPath.toFile(), false); }
		else { Files.createDirectory(tempPath); }
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

	private String getWrapUpPrompt() {
		try { return(Easy.stringFromResource("wrapUpPrompt.md")); }
		catch (Exception e) { log.warning(Easy.exMsg(e, "wrapup", true)); return(null); }
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

	private final static String DATA_DIR = "data";
	private final static String TEMP_SUBDIR = "temp";
	
	private final static String CHILDREN_DIR = "children";
	private final static String CONVERSATIONS_DIR = "conversations";
	private final static String SCRIPTS_DIR = "scripts";

	private final static String DATA_DIR_ENV = "DATA_DIR";

	private final static int PROCESS_TIMEOUT_SECONDS = 60 * 20; // 20 minutes

	private final static String PAUSED_SUFFIX = ".paused";
	
	// +---------+
	// | Members |
	// +---------+

	private Path projectPath;
	private Conversation.Config parentCfg;
	private Conversation.Config thisCfg;
	private String parentPrompt;
	private String thisPrompt;
	
	private final static Logger log = Logger.getLogger(Project.class.getName());
}
