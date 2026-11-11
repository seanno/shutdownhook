/*
** ADVENTURE.JAVA
*/

package com.shutdownhook.colossus;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.LineTransformer;

import com.shutdownhook.colossus.ToolCalling.Tool;
import com.shutdownhook.colossus.ToolCalling.ToolClass;

public class Adventure implements Closeable
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String StatePath = "/tmp/adventure";
		public int MaxRunExceptions = 10;
		
		public PromptedProcess.Config Process = new PromptedProcess.Config();
		public Conversation.Config Conversation = new Conversation.Config();

		public String AdventureToolName = "adventure_tool";
		
		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}

	public Adventure(Config cfg) throws Exception {
		
		this.cfg = cfg;
		setupAdventureTool();
		this.process = new PromptedProcess(cfg.Process);
	}

	public void close() {
		process.close();
	}

	// +-----+
	// | run |
	// +-----+

	public void run() throws Exception {

		int runExceptions = 0;
		StringBuilder sb = new StringBuilder();
		String processMessage = process.turn(null);

		while (processMessage != null) {

			Conversation conversation = null;
			Adventure_Tool adventureTool = null;
			
			try {
				conversation = new Conversation(cfg.Conversation);
				adventureTool = (Adventure_Tool) conversation.findTool(cfg.AdventureToolName);
				adventureTool.addNotepadMessages(conversation);
									
				sb.setLength(0);
				sb.append("===== ADVENTURE\n")
					.append(processMessage.trim())
					.append("\n");

				System.out.println(sb.toString());
				adventureTool.appendToHistory(sb.toString());

				String modelMessage = conversation.prompt(processMessage);

				if (modelMessage == null) {
					log.info("Model terminated conversation");
					process.quit();
					return;
				}

				sb.setLength(0);
				sb.append("===== MODEL\n")
					.append(modelMessage.trim())
					.append("\n");

				System.out.println(sb.toString());
				adventureTool.appendToHistory(sb.toString());

				processMessage = process.turn(modelMessage);
			}
			catch (Exception e) {
				++runExceptions;
				log.warning(Easy.exMsg(e, "Adventure", true));
				if (adventureTool != null) {
					String hist = String.format("!!!!! Exception %d: %s\n", runExceptions, e.toString());
					adventureTool.appendToHistory(hist);
				}
				if (runExceptions > cfg.MaxRunExceptions) throw(e);
			}
			finally {
				Easy.safeClose(conversation);
			}
		}

		log.info("Process terminated");
	}

	// +-----------------+
	// | PromptedProcess |
	// +-----------------+

	public static class PromptedProcess implements Closeable
	{
		public static class Config
		{
			public String CommandLine = "/usr/local/bin/advent-noflush";
			public String Prompt = "\n> ";
			public String CommandSend = "\n";
			public String QuitCommand = "quit\nyes\n";
			public int ExitTimeoutSeconds = 2;
		}

		public PromptedProcess(Config cfg) {
			this.cfg = cfg;
		}

		public void close() {
			Easy.safeClose(writer);
			Easy.safeClose(output);
			Easy.safeClose(buffered);
			Easy.safeClose(reader);
			Easy.safeClose(input);

			if (process != null) {
				try {
					if (!process.waitFor(cfg.ExitTimeoutSeconds,
										 TimeUnit.SECONDS)) {
						process.destroyForcibly();
					}
				}
				catch (Exception e) {
					// eat it
				}
				
			}
		}

		// null return means process is finished
		
		public String turn(String command) {

			if (process == null && !start()) {
				return(null);
			}

			if (command != null) {
				String cleanCommand = command.replace(cfg.CommandSend, " ");

				try {
					writer.write(cleanCommand + cfg.CommandSend);
					writer.flush();
				}
				catch (Exception e) {
					log.warning("Exception writing to process: " + e.toString());
					return(null);
				}
			}

			StringBuilder sb = new StringBuilder();
			int ch;

			try {
				while ((ch = buffered.read()) != -1) {
					sb.append((char)ch);
					if (sb.toString().endsWith(cfg.Prompt)) {
						return(sb.toString().substring(0, sb.length() - cfg.Prompt.length()));
					}
				}
			}
			catch (Exception e) {
				log.warning("Exception reading stream: " + e.toString());
				// then fall through and return what we have if anything
			}

			// null here is fine, just means the process ended happily
			return(sb.length() == 0 ? null : sb.toString());
		}

		public String quit() {
			return(turn(cfg.QuitCommand));
		}
		
		private boolean start() {
			
			String[] commands = new String[] { "bash", "-c", cfg.CommandLine };
			ProcessBuilder pb = new ProcessBuilder(commands);

			try {
				process = pb.start();
				
				input = process.getInputStream();
				reader = new InputStreamReader(input);
				buffered = new BufferedReader(reader);

				output = process.getOutputStream();
				writer = new OutputStreamWriter(output);

				return(true);
			}
			catch (Exception e) {
				log.warning("Exception starting process: " + e.toString());
				return(false);
			}
			

		}

		private Config cfg; 
		private Process process = null;
		private InputStream input = null;
		private InputStreamReader reader = null;
		private BufferedReader buffered = null;
		private OutputStream output = null;
		private OutputStreamWriter writer = null;
	}

	// +---------------+
	// | AdventureTool |
	// +---------------+

	// This is totally non-reusable; part of an experiment to see if
	// Colossus can learn to play Adventure thoughtfully

	public static class Adventure_Tool implements Tool
	{
		public static enum Notepad
		{
			STATE,
			MAP,
			LOG,
			HISTORY
		}

		public static enum Action
		{
			READ,
			SEARCH,
			APPEND,
			INSERT,
			DELETE,
			REPLACE
		}
		
		public static class Config
		{
			public String StatePath;
		}

		public JsonObject initialize(ToolClass toolClass, Conversation conversation) throws Exception {
			this.cfg = ToolCalling.loadConfig(toolClass, Config.class);
			this.path = Paths.get(cfg.StatePath);
			this.notepadMessages = new HashMap<Notepad, OpenAI.Message>();
			return(ToolCalling.getToolDescriptionFromSmartyPath(toolClass, "@adventure_tool.json"));
		}
		
		public String execute(JsonObject arguments, Conversation conversation) throws Exception {
			
			Notepad notepad = Notepad.valueOf(ToolCalling.getStringField(arguments, "notepad"));
			Action action = Action.valueOf(ToolCalling.getStringField(arguments, "action"));
			boolean readOnlyAction = (action.equals(Action.READ) || action.equals(Action.SEARCH));

			if (notepad.equals(Notepad.HISTORY) && !readOnlyAction) {
				throw new Exception("HISTORY is a read-only notepad");
			}

			synchronized (notepadMessages.get(notepad)) {
				String result = executeLocked(notepad, action, arguments, conversation);
				if (!readOnlyAction) updateNotepadMessage(conversation, notepad);
				return(result);
			}
		}
		
		private String executeLocked(Notepad notepad, Action action,
									 JsonObject arguments, Conversation conversation) throws Exception {

			int lineIndexStart = ToolCalling.getIntegerField(arguments, "line_index_start", 0);
			int lineIndexEndInclusive = ToolCalling.getIntegerField(arguments, "line_index_end_inclusive", -1);
			String search = ToolCalling.getStringField(arguments, "search", null);
			String lines = ToolCalling.getStringField(arguments, "lines", null);

			log.info(String.format("ADVENTURE_TOOL: %s %s (%d:%d) %s (%d)",
								   notepad, action, lineIndexStart, lineIndexEndInclusive,
								   search, (lines == null ? -1 : lines.length())));
			
			Path notepadPath = getNotepadPath(notepad);
			String result = "OK";
			
			switch (action) {
				case READ:
					result = LineTransformer.read(notepadPath, lineIndexStart, lineIndexEndInclusive);
					break;

				case SEARCH:
					if (search == null) throw new Exception("'search' parameter is required.");
					result = LineTransformer.search(notepadPath, search);
					break;

				case APPEND:
					if (lines == null) throw new Exception("'lines' parameter is required.");
					LineTransformer.append(notepadPath, lines);
					break;
					
				case INSERT:
					if (lines == null) throw new Exception("'lines' parameter is required.");
					LineTransformer.insert(notepadPath, lineIndexStart, lines);
					break;

				case DELETE:
					LineTransformer.delete(notepadPath, lineIndexStart, lineIndexEndInclusive);
					break;
					
				case REPLACE:
					if (lines == null) throw new Exception("'lines' parameter is required.");
					LineTransformer.replace(notepadPath, lineIndexStart, lineIndexEndInclusive, lines);
					break;
			}

			return(result);
		}

		public void addNotepadMessages(Conversation conversation) throws Exception {
			addNotepadMessage(conversation, Notepad.STATE);
			addNotepadMessage(conversation, Notepad.MAP);
			addNotepadMessage(conversation, Notepad.LOG);
			notepadMessages.put(Notepad.HISTORY, new OpenAI.Message());
		}

		private void addNotepadMessage(Conversation conversation, Notepad notepad) throws Exception {
			OpenAI.Message notepadMessage = conversation.addUserMessage(getNotepadContents(notepad));
			notepadMessages.put(notepad, notepadMessage);
		}
		
		public void updateNotepadMessage(Conversation conversation, Notepad notepad) throws Exception {
			OpenAI.Message notepadMessage = notepadMessages.get(notepad);
			synchronized (notepadMessage) {
				notepadMessage.content = getNotepadContents(notepad);
			}
		}
		
		public void appendToHistory(String lines) throws IOException {
			// don't need to lock because doesn't happen from parallel tools
			Path notepadPath = getNotepadPath(Notepad.HISTORY);
			LineTransformer.append(notepadPath, lines);
		}

		private String getNotepadContents(Notepad notepad) throws Exception {
			Path notepadPath = getNotepadPath(notepad);
			return("## " + notepad.toString() + "\n" + LineTransformer.readAll(notepadPath));
		}

		private Path getNotepadPath(Notepad notepad) {
			return(path.resolve(notepad.toString() + ".txt"));
		}

		private Config cfg;
		private Path path;
		private Map<Notepad,OpenAI.Message> notepadMessages;
 	}

	private void setupAdventureTool() throws IOException {

		Path statePath = Paths.get(cfg.StatePath);
		Files.createDirectories(statePath);
		log.info("Saving Adventure_Tool files to " + statePath.toString());
		
		ToolCalling.ToolClass adventureTool = new ToolCalling.ToolClass();
		adventureTool.ClassName = Adventure_Tool.class.getName();
		adventureTool.Name = cfg.AdventureToolName;
		adventureTool.Config = new JsonObject();
		adventureTool.Config.addProperty("StatePath", cfg.StatePath);

		cfg.Conversation.ToolClasses = (cfg.Conversation.ToolClasses == null
			? new ToolCalling.ToolClass[1]
			: Arrays.copyOf(cfg.Conversation.ToolClasses, cfg.Conversation.ToolClasses.length + 1));

		cfg.Conversation.ToolClasses[cfg.Conversation.ToolClasses.length - 1] = adventureTool;
	}


	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String args[]) throws Exception {
		
		Easy.configureLoggingProperties("@logging-adventure.properties");
		
		Adventure adventure = null;

		try {
			Config cfg = Config.fromJson(Easy.stringFromFile(args[0]));
			adventure = new Adventure(cfg);
			adventure.run();
		}
		finally {
			Easy.safeClose(adventure);
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private PromptedProcess process;
	
	private final static Logger log = Logger.getLogger(Adventure.class.getName());
}

