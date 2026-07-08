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

	public Project(String path, Conversation.Config parentCfg) throws Exception {
		this.projectPath = Paths.get(path);
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

	public boolean run(List<ProjectResult> results, String parentName) {

		ProjectResult result = new ProjectResult();
		results.add(result);
		
		result.Name = (parentName == null ? "" : parentName + " : ") + projectPath.getFileName();
		result.Started = Instant.now().toString();
		
		log.info(">>>>> STARTED project: " + projectPath.toString());
		Instant started = Instant.now();
		
		Conversation conversation = null;
		
		try {
			// prework
			ensureDataAndClearTemp();
			runScript(PRE_SCRIPT_FILE);

			// project conversation
			Path prompt = getProjectFile(PROMPT_FILE);
			if (Files.exists(prompt)) {
				
				conversation = new Conversation(thisCfg);
				result.Response = conversation.safePrompt(Easy.stringFromFile(prompt.toString()));

				archiveConversation(conversation);
			}

			// child projects
			Path children = getProjectDirectory(CHILDREN_DIR);
			for (Path childPath : Files.list(children).toList()) {
				Project childProject = new Project(childPath.toString(), thisCfg);
				childProject.run(results, result.Name);
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
			archiveRun(result);;
			Easy.safeClose(conversation);
		}
	}
	
	// +---------------------+
	// | archiveConversation |
	// | archiveRun          |
	// +---------------------+

	private void archiveConversation(Conversation conversation) throws Exception {
				
		String fileNameFormat = conversation.getEnv().getFileStamp() + "-Conversation%s.json";
		
		Path archiveDir = getProjectDirectory(CONVERSATIONS_DIR);
		Path archiveFile;
		int counter = 0;
		
		do {
			String tag = (counter == 0 ? "" : "-" + Integer.toString(counter));
			archiveFile = archiveDir.resolve(String.format(fileNameFormat, tag));
			counter++;
		}
		while (Files.exists(archiveFile));

		Easy.stringToFile(archiveFile.toString(), conversation.history());
	}
	
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

		// 1. start from convo config located here or at parent
		
		Path projectCfg = getProjectFile(CONVERSATION_CONFIG_FILE);

		thisCfg = (Files.exists(projectCfg)
				   ? Conversation.Config.fromJson(Easy.stringFromFile(projectCfg.toString()))
				   : (parentCfg != null
					  ? parentCfg.clone()
					  : new Conversation.Config()));

		// 2. inherit the system prompt

		Path projectSys = getProjectFile(SYSTEMPROMPT_FILE);
		
		if (Files.exists(projectSys)) {
			thisCfg.SystemPrompt =
				(thisCfg.SystemPrompt == null ? "" : thisCfg.SystemPrompt + "\n") +
				Easy.stringFromFile(projectSys.toString());
		}

		// 3. force the working directory for file and code tools if present

		List<ToolClass> thisTools = new ArrayList<ToolClass>();

		String fileToolClass = ToolCalling.Files_Tool.class.getName();
		String codeToolClass = ToolCalling.Code_Tool.class.getName();
		String dataDir = getProjectDirectory(DATA_DIR).toString();

		if (thisCfg.ToolClasses != null) {
			
			for (ToolClass toolClass : thisCfg.ToolClasses) {
				
				if (toolClass == null) continue; // defense against stray trailing commas in config
				
				if (fileToolClass.equals(toolClass.ClassName) || codeToolClass.equals(toolClass.ClassName)) {
					if (toolClass.Config == null) toolClass.Config = new JsonObject();
					toolClass.Config.addProperty("BasePath", dataDir);
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
		
		Path scriptsDir = getProjectDirectory(SCRIPTS_DIR).toAbsolutePath();
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

	private Path getProjectDirectory(String dir) throws Exception {
		Path path = projectPath.resolve(dir);
		if (!Files.exists(path)) Files.createDirectory(path);
		return(path);
	}

	private Path getProjectSubDirectory(String dir, String subdir) throws Exception {
		Path path = getProjectDirectory(dir).resolve(subdir);
		if (!Files.exists(path)) Files.createDirectory(path);
		return(path);
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

	private final static String DATA_DIR = "data";
	private final static String TEMP_SUBDIR = "temp";
	
	private final static String CHILDREN_DIR = "children";
	private final static String CONVERSATIONS_DIR = "conversations";
	private final static String SCRIPTS_DIR = "scripts";

	private final static String DATA_DIR_ENV = "DATA_DIR";

	private final static int PROCESS_TIMEOUT_SECONDS = 60 * 10; // 10 minutes
		
	// +---------+
	// | Members |
	// +---------+

	private Path projectPath;
	private Conversation.Config parentCfg;
	private Conversation.Config thisCfg;
	
	private final static Logger log = Logger.getLogger(Project.class.getName());
}
