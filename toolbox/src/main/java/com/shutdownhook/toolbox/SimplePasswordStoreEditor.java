/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.SimplePasswordStore.Credential;


public class SimplePasswordStoreEditor implements Closeable
{
	// +--------------------+
	// | Setup & Entrypoint |
	// +--------------------+

	public SimplePasswordStoreEditor(String configPath) {

		this.configFile = new File(configPath);
	}

	public void close()
	{
		if (scanner != null) Easy.safeClose(scanner);
	}

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			System.err.println("Usage:\n" +
							   "java -cp ... \\\n" +
							   "\tcom.shutdownhook.toolbox.SimplePasswordStoreEditor \\\n" +
							   "\t[PATH_TO_CONFIG]");
			return;
		}

		String configPath = args[0];

		SimplePasswordStoreEditor editor = new SimplePasswordStoreEditor(configPath);
		editor.go();
		editor.close();
	}

	// +----+
	// | go |
	// +----+

	public void go() throws Exception {
		
		// Load existing JSON

		if (configFile.exists()) {
			String configJson = Easy.stringFromFile(configFile.getAbsolutePath());
			SimplePasswordStore.Config cfg = new Gson().fromJson(configJson, SimplePasswordStore.Config.class);
			this.store = new SimplePasswordStore(cfg);
		}
		else {
			this.store = new SimplePasswordStore();
		}

		// Loop

		boolean dirty = commandLoop(store);

		if (!dirty) {
			print("no changes made");
			return;
		}

		// Write out new JSON

		String configPath = configFile.getAbsolutePath();

		if (configFile.exists()) {
			
			print("Backing up oldJSON to %s.bak", configPath);

			Files.copy(Paths.get(configPath), Paths.get(configPath + ".bak"),
					   StandardCopyOption.REPLACE_EXISTING);
		}
			
		print("Writing new JSON to %s", configPath);

		String newJson = new GsonBuilder().setPrettyPrinting().create().toJson(store.getConfig());
			
		Easy.stringToFile(configPath, newJson);

		print("done!");
	}

	private boolean commandLoop(SimplePasswordStore store) throws Exception {

		boolean dirty = false;
		this.scanner = new Scanner(System.in);

		print("'exit' to quit and save; ^C to quit without saving. '?' for command list.");

		while (true) {

			String cmd = prompt("cmd", "?");
				
			switch (cmd) {
				case "?": usage(); break;
				case "exit": return(dirty);
				default: print("Unknown command"); break;

				case "list": listUsers(); break;
				case "upsert": if (upsertUser()) dirty = true; break;
				case "remove": if (removeUser()) dirty = true; break;
					
				case "listprops": listProperties(); break;
				case "addprop": if (addProperty()) dirty = true; break;
				case "delprop": if (removeProperty()) dirty = true; break;
			}
		}
	}

	private void usage() {
		
		print("?         this help\n" +
			  "^C        quit without saving\n" +
			  "exit      quit and save\n" +
			  "\n" +
			  "list      list users\n" +
			  "upsert    add or modify user\n" +
			  "remove    remove user\n" +
			  "\n" +
			  "listprops list properties for an existing user\n" +
			  "addprop   add a property to an existing user\n" +
			  "delprop   remove a property from an existing user\n");
	}

	// +---------+
	// | Actions |
	// +---------+
	
	private void listUsers() {

		SimplePasswordStore.Config cfg = store.getConfig();
		
		if (cfg.Credentials == null || cfg.Credentials.size() == 0) {
			print("No users in config");
			return;
		}
		
		for (Credential cred : cfg.Credentials) {
			print(cred.User);
		}
	}
	
	private boolean upsertUser() {

		String user = prompt("user", "");
		if (user.isEmpty()) return(false);
		
		String pass = prompt("password", "");
		if (pass.isEmpty()) return(false);

		store.upsert(user, pass);
		print("Upserted user " + user);
		return(true);
	}

	private boolean removeUser() {
		
		String user = prompt("user", "");
		if (user.isEmpty()) return(false);

		store.delete(user);
		print("Deleted user " + user);
		return(true);
	}

	private void listProperties() {
		
		String user = prompt("user", "");
		if (user.isEmpty()) return;

		Map<String,String> props = store.getProperties(user);

		if (props == null || props.isEmpty()) {
			print("no properties found");
			return;
		}

		for (String prop : props.keySet()) {
			print(String.format("%s\t%s", prop, props.get(prop)));
		}
	}
	
	private boolean addProperty() {
		
		String user = prompt("user", "");
		if (user.isEmpty()) return(false);
		
		String prop = prompt("property", "");
		if (prop.isEmpty()) return(false);

		String val = prompt("value", "");
		if (val.isEmpty()) return(false);

		if (!store.addProperty(user, prop, val)) return(false);
		
		print(String.format("Added property %s for user %s", prop, user));
		return(true);
	}

	private boolean removeProperty() {

		String user = prompt("user", "");
		if (user.isEmpty()) return(false);
		
		String prop = prompt("property", "");
		if (prop.isEmpty()) return(false);

		if (!store.removeProperty(user, prop)) return(false);
		
		print(String.format("Removed property %s for user %s", prop, user));
		return(true);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String prompt(String prompt, String defaultVal, Object ... args) {
		String suffix = (Easy.nullOrEmpty(defaultVal) ? ": " : String.format(" [%s]: ", defaultVal));
		System.out.print(String.format(prompt, args) + suffix);

		String response = scanner.nextLine().trim();
		return(response.isEmpty() ? defaultVal : response);
	}

	private void print(String format, Object ... args) {
		String msg = String.format(format, args);
		System.out.println(msg);
	}

	// +---------+
	// | Members |
	// +---------+

	private File configFile;
	
	private Scanner scanner;
	private SimplePasswordStore store;

	private final static Logger log = Logger.getLogger(SimplePasswordStoreEditor.class.getName());
}
