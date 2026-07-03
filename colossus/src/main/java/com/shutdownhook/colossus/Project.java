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
		this.fileToolCfgTemplate = new Template(Easy.stringFromResource("project_file_tool_config.json.tmpl"));
		
		setupConversationConfig();
	}

	// +-----+
	// | run |
	// +-----+

	public boolean run() {

		log.info(">>>>> STARTED project: " + projectPath.toString());
		Instant started = Instant.now();
		
		Conversation conversation = null;
		
		try {
			// prework
			ensureAndClearTemp();
			runScript(PRE_SCRIPT_FILE);

			// project conversation
			Path prompt = getProjectFile(PROMPT_FILE);
			if (Files.exists(prompt)) {
				
				getProjectDirectory(DATA_DIR);
				getProjectDirectory(TEMP_DIR);

				conversation = new Conversation(thisCfg);
				conversation.prompt(Easy.stringFromFile(prompt.toString()));

				archiveConversation(conversation);
			}

			// child projects
			Path children = getProjectDirectory(CHILDREN_DIR, false);
			if (Files.exists(children)) {
				for (Path childPath : Files.list(children).toList()) {
					Project childProject = new Project(childPath.toString(), thisCfg);
					childProject.run();
				}
			}

			// postwork
			runScript(POST_SCRIPT_FILE);
			ensureAndClearTemp();

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
			
			return(false);
		}
		finally {
			Easy.safeClose(conversation);
		}
	}
	
	// +---------------------+
	// | archiveConversation |
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

	// +-------------------------+
	// | setupConversationConfig |
	// +-------------------------+

	private void setupConversationConfig() throws Exception {

		// 1. start from convo config located here or at parent
		
		Path projectCfg = getProjectFile(CONVERSATION_CONFIG_FILE);
		
		thisCfg = (!Files.exists(projectCfg) ? parentCfg.clone()
				   : Conversation.Config.fromJson(Easy.stringFromFile(projectCfg.toString())));

		// 2. inherit the system prompt

		Path projectSys = getProjectFile(SYSTEMPROMPT_FILE);
		
		if (Files.exists(projectSys)) {
			thisCfg.SystemPrompt =
				(thisCfg.SystemPrompt == null ? "" : thisCfg.SystemPrompt + "\n") +
				Easy.stringFromFile(projectSys.toString());
		}

		// 3. remove any existing data/temp tools and add new ones for this project

		List<ToolClass> thisTools = new ArrayList<ToolClass>();

		if (thisCfg.ToolClasses != null) {
			for (ToolClass toolClass : thisCfg.ToolClasses) {
				if (toolClass == null) continue; // defense against stray trailing commas in config
				if (!DATA_TOOL_NAME.equals(toolClass.Name) && !TEMP_TOOL_NAME.equals(toolClass.Name)) {
					thisTools.add(toolClass);
				}
			}
		}

		thisTools.add(setupFileTool(DATA_TOOL_NAME, DATA_TOOL_TYPE, getProjectDirectory(DATA_DIR, false)));
		thisTools.add(setupFileTool(TEMP_TOOL_NAME, TEMP_TOOL_TYPE, getProjectDirectory(TEMP_DIR, false)));

		thisCfg.ToolClasses = thisTools.toArray(new ToolClass[thisTools.size()]);
	}

	private ToolClass setupFileTool(String name, String type, Path basePath) throws Exception {

		Map<String,String> params = new HashMap<String,String>();
		params.put("TOOL_NAME", Utility.escapeJsonString(name));
		params.put("TOOL_TYPE", Utility.escapeJsonString(type));
		params.put("TOOL_BASE", Utility.escapeJsonString(basePath.toString()));

		String json = fileToolCfgTemplate.render(params);
		return(ToolClass.fromJson(json));
	}

	// +-----------+
	// | runScript |
	// +-----------+

	private boolean runScript(String script) throws Exception {
		
		Path scriptsDir = getProjectDirectory(SCRIPTS_DIR, false);
		if (!Files.exists(scriptsDir)) return(true);
		Path scriptFile = scriptsDir.resolve(script);
		if (!Files.exists(scriptFile)) return(true);

		String[] commands = new String[] { "bash", "-c", scriptFile.toString() };
		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.directory(scriptsDir.toFile());
		pb.environment().put(DATA_DIR_ENV, getProjectDirectory(DATA_DIR, true).toString());
		pb.environment().put(TEMP_DIR_ENV, getProjectDirectory(TEMP_DIR, true).toString());
		
		Process p = pb.start();
		p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		return(p.exitValue() == 0);
	}
							  
	// +--------------------+
	// | ensureAndClearTemp |
	// +--------------------+

	private void ensureAndClearTemp() throws Exception {
		Path tempPath = getProjectDirectory(TEMP_DIR, false);
		
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
		return(getProjectDirectory(dir, true));
	}
	
	private Path getProjectDirectory(String dir, boolean createIfNeeded) throws Exception {
		Path path = projectPath.resolve(dir);
		if (!Files.exists(path) && createIfNeeded) Files.createDirectory(path);
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

	private final static String DATA_DIR = "data";
	private final static String TEMP_DIR = "temp";
	private final static String CHILDREN_DIR = "children";
	private final static String CONVERSATIONS_DIR = "conversations";
	private final static String SCRIPTS_DIR = "scripts";

	private final static String DATA_DIR_ENV = "DATA_DIR";
	private final static String TEMP_DIR_ENV = "TEMP_DIR";

	private final static int PROCESS_TIMEOUT_SECONDS = 60 * 10; // 10 minutes
		
	private final static String DATA_TOOL_NAME = "persistent_textfile_access";
	private final static String DATA_TOOL_TYPE = "PERSISTENT";
	
	private final static String TEMP_TOOL_NAME = "temporary_textfile_access";
	private final static String TEMP_TOOL_TYPE = "TEMPORARY";

	// +---------+
	// | Members |
	// +---------+

	private Path projectPath;
	private Conversation.Config parentCfg;
	private Conversation.Config thisCfg;
	private Template fileToolCfgTemplate;
	
	private final static Logger log = Logger.getLogger(Project.class.getName());
}
