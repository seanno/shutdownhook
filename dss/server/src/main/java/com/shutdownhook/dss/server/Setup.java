/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Encrypt;

public class Setup
{
	private final static String DEFAULT_CONFIG = "config.json";
	private final static int DEFAULT_PORT = 7071;
	private final static String DEFAULT_STORE_LOC = "/tmp/dss.sql";

	private final static String KEY_ALG = "AES";
	
	// +-------+
	// | Setup |
	// +-------+

	public Setup(String configPath) throws IOException {

		this.gson = new GsonBuilder().setPrettyPrinting().create();
		
		this.configFile = new File(configPath);
		this.dirty = false;

		this.cfg = (configFile.exists()
					? Server.Config.fromJson(Easy.stringFromFile(configPath))
					: new Server.Config());
	}

	// +--------------+
	// | save & dirty |
	// +--------------+

	public boolean isNew() {
		return(!configFile.exists());
	}

	public boolean isDirty() {
		return(dirty);
	}

	public void save() throws Exception {
		
		if (!dirty) return;

		String json = gson.toJson(cfg);
		Easy.stringToFile(configFile.getAbsolutePath(), json);
		dirty = false;
	}

	// +-----+
	// | SSL |
	// +-----+

	public Integer currentPort() {
		return(cfg.WebServer.Port);
	}

	public void setPort(int port) {
		cfg.WebServer.Port = port;
		dirty = true;
	}

	public String currentCertificateFile() {
		return(cfg.WebServer.SSLCertificateFile);
	}
	
	public String currentCertificateKeyFile() {
		return(cfg.WebServer.SSLCertificateKeyFile);
	}

	public void enableSSL(String sslCertificateFile,
						  String sslCertificateKeyFile) {

		cfg.WebServer.SSLCertificateFile = sslCertificateFile;
		cfg.WebServer.SSLCertificateKeyFile = sslCertificateKeyFile;
		dirty = true;
	}

	public void disableSSL() {
		enableSSL(null, null);
	}
							 
	// +-------------------------+
	// | Store Connection String |
	// +-------------------------+

	public String getStoreConnectionString() {
		return(cfg.Sql.ConnectionString);
	}

	public void setStoreConnectionString(String cs) {
		cfg.Sql.ConnectionString = cs;
		dirty = true;
	}

	// +-------------+
	// | Cookie Keys |
	// +-------------+

	public boolean haveRolledKeys() {
		return(cfg.WebServer.CookieEncrypt != null &&
			   cfg.WebServer.CookieEncrypt.RolledKeys != null &&
			   cfg.WebServer.CookieEncrypt.RolledKeys.length > 0);
	}

	public boolean haveActiveKey() {
		return(cfg.WebServer.CookieEncrypt != null &&
			   cfg.WebServer.CookieEncrypt.ActiveKey != null);
	}
	
	public void newKey(boolean rollCurrent) {
		
		Encrypt.Config enc = cfg.WebServer.CookieEncrypt;
		
		if (enc == null) {
			enc = new Encrypt.Config();
			cfg.WebServer.CookieEncrypt = enc;
		}

		if (enc.ActiveKey != null && rollCurrent) {
				
			enc.RolledKeys = (enc.RolledKeys == null
							  ? new Encrypt.TaggedKey[1]
							  : Arrays.copyOf(enc.RolledKeys, enc.RolledKeys.length + 1));
				
				
			enc.RolledKeys[enc.RolledKeys.length - 1] = enc.ActiveKey;
		}

		enc.ActiveKey = new Encrypt.TaggedKey();
		enc.ActiveKey.Tag = Long.toString(Instant.now().toEpochMilli());
		enc.ActiveKey.Key = Encrypt.generateKey(Setup.KEY_ALG);
		dirty = true;
	}

	public int expireRolledKeys() {
		
		Encrypt.Config enc = cfg.WebServer.CookieEncrypt;

		if (enc == null || enc.RolledKeys == null || enc.RolledKeys.length == 0) return(0);

		int count = enc.RolledKeys.length;

		enc.RolledKeys = null;
		dirty = true;
		
		return(count);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Gson gson;
	private Scanner scanner;
	private Server.Config cfg;
	private File configFile;
	private boolean dirty;

	// +---------+
	// | SetupUX |
	// +---------+

	public static class SetupUX implements Closeable
	{
		public SetupUX(String configPath) throws IOException {
			scanner = new Scanner(System.in);
			setup = new Setup(configPath);

			if (setup.isNew()) print("Initializing new config at '%s'", configPath);
			else print("Loading existing config at '%s'", configPath);
		}

		public void close() {
			if (scanner != null) scanner.close();
		}

		public void commandLoop() throws Exception {
		
			boolean keepGoing = true;
			print("? for command list");

			while (keepGoing) {

				String cmd = prompt("cmd", "");
				
				switch (cmd) {
				
					case "usage": case "help": case "?": usage(); break;
					case "exit": case "quit": if (confirmExit()) keepGoing = false; break;

					case "init":           init();               break;
					case "save":           save();               break;
					case "new_key":        newKey();             break;
					case "expire_rolled":  expireRolledKeys();   break;
					case "ssl":            ssl();                break;
					case "port":           port();               break;
					case "store":          store();              break;
				}
			}
		}

		private void usage() {
			print("  exit/quit        Quit \n" +
				  "  usage/help       This information \n" +
				  "  save             Save changes \n" +
				  "  init             Set basic values \n" +
				  "  new_key          Generate new cookie encryption key \n" +
				  "  expire_rolled    Expire rolled cookie encryption keys \n" +
				  "  ssl              Configure HTTP(s) \n" +
				  "  port             Configure listening port \n" +
				  "  store            Set metadata store connection string \n" +            
				  "");
		}

		private boolean confirmExit() {
			if (!setup.isDirty()) return(true);
			return(confirm("You have unsaved changes, are you sure", false));
		}

		private void save() throws Exception {

			if (!setup.isDirty()) {
				print("No changes need to be saved.");
				return;
			}

			setup.save();
			print("Saved.");
		}

		// Init

		private void init() throws Exception {
			setup.newKey(true);
			store();
			port();
			ssl();
			save();
		}

		// Store

		private void store() throws Exception {

			String currentLoc = setup.getStoreConnectionString().replace("jdbc:sqlite:","");
			if (currentLoc == null) currentLoc = Setup.DEFAULT_STORE_LOC;
			
			String newLoc = prompt("location for metadata file", currentLoc);

			setup.setStoreConnectionString("jdbc:sqlite:" + newLoc);
			print("updated.");
		}

		// Keys

		private void newKey() throws Exception {

			boolean rollCurrent =
				(setup.haveActiveKey() && confirm("save current key to rolled list", true));

			setup.newKey(rollCurrent);
			print("new key created");
		}

		private void expireRolledKeys() throws Exception {
		
			int count = setup.expireRolledKeys();

			if (count == 0) {
				print("No rolled keys to expire");
				return;
			}

			print("Expired %d rolled keys", count);
		}

		// HTTP

		private void port() {

			Integer currentPort = setup.currentPort();
			if (currentPort == null) currentPort = Setup.DEFAULT_PORT;
				
			String newPortStr = prompt("Listen on port", currentPort.toString());
			
			try {
				int newPort = Integer.parseInt(newPortStr);
				if (newPort < 1) { print("invalid port."); return; }
				
				setup.setPort(newPort);
				print("updated.");
			}
			catch (Exception e) {
				print("not a number; aborted.");
			}
		}
		
		private void ssl() {

			if (confirm("Use SSL", true)) {
				
				print("Use the same file formats as Apache mod_ssl.");

				String currentCert = setup.currentCertificateFile();
				String cert = prompt("(1 of 2) Local path to Certificate file", currentCert);
				if (!new File(cert).exists()) { print("file doesn't exist; aborted."); return; }
				
				String currentKey = setup.currentCertificateKeyFile();
				String key = prompt("(2 of 2) Local path to Certificate Key file", currentKey);
				if (!new File(key).exists()) { print("file doesn't exist; aborted."); return; }

				setup.enableSSL(cert, key);
				print("updated.");
			}
			else {
				setup.disableSSL();
				print("disabled.");
			}
		}
			

		// Helpers
		
		private String prompt(String prompt, String defaultVal, Object ... args) {
			String suffix = (Easy.nullOrEmpty(defaultVal) ? ": " : String.format(" [%s]: ", defaultVal));
			System.out.print(String.format(prompt, args) + suffix);

			String response = scanner.nextLine().trim();
			return(response.isEmpty() ? defaultVal : response);
		}
	
		private boolean confirm(String prompt, boolean defaultVal, Object ... args) {

			String suffix = String.format(" (y/n)? [%s]: ", defaultVal ? "YES" : "NO");
			System.out.print(String.format(prompt, args) + suffix);
			
			String response = scanner.nextLine().trim().toLowerCase();
			return(response.isEmpty() ? defaultVal : response.startsWith("y"));
		}

		private static void print(String format, Object ... args) {
			String msg = String.format(format, args);
			System.out.println(msg);
		}

		// Members
		
		private Scanner scanner;
		private Setup setup;
	}
	
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		SetupUX ux = new SetupUX(args.length >= 1 ? args[0] : DEFAULT_CONFIG);
		boolean initAndQuit = (args.length >= 2 && args[1].equals("init"));
		
		if (initAndQuit) ux.init();
		else ux.commandLoop();
		
		ux.close();
	}
}
