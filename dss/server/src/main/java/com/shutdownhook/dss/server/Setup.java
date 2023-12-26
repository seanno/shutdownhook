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
import java.util.NoSuchElementException;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Encrypt;
import com.shutdownhook.toolbox.OAuth2Login;

public class Setup
{
	private final static String DEFAULT_CONFIG = "config.json";
	private final static int DEFAULT_PORT = 3001;
	private final static String DEFAULT_STORE_LOC = "/tmp/dss.sql";

	private final static String DEFAULT_DEV_OAUTH2 = "test|test@example.com|test_token";

	private final static String DEFAULT_PROVIDER = "github_reauth";
	private final static String DEFAULT_CLIENT_ID = "CLIENT_ID";
	private final static String DEFAULT_CLIENT_SECRET = "CLIENT_SECRET";
		
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

	// +------------------+
	// | getConfigSubTree |
	// +------------------+
	
	public String getConfigSubTree(String path) throws Exception {

		JsonElement json = gson.toJsonTree(cfg);

		if (path.equals(".")) path = "";
		String[] fields = path.split("\\.");

		try {
			for (String field : fields) {
				if (!field.isEmpty()) json = json.getAsJsonObject().get(field);
				if (json == null) return(null);
			}
		}
		catch (Exception e) {
			return(null);
		}

		return(gson.toJson(json));
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

	// +--------+
	// | OAuth2 |
	// +--------+

	public static class OAuth2Details
	{
		public String Provider;
		public String ClientId;
		public String ClientSecret;
		public String AuthURL;
		public String TokenURL;
		public String Scope;
	}

	public String getOAuth2RedirectURL() {

		boolean sslEnabled = (cfg.WebServer.SSLCertificateFile != null);
		
		String redirectPath = (cfg.WebServer.OAuth2 == null
							   ? OAuth2Login.DEFAULT_REDIRECT_PATH
							   : cfg.WebServer.OAuth2.RedirectPath);

		String url = String.format("http%s://YOUR_SERVER:%d%s",
								   sslEnabled ? "s" : "",
								   cfg.WebServer.Port,
								   redirectPath);
		return(url);
	}
	
	public OAuth2Details getOAuth2Details() {
		
		OAuth2Details details = new OAuth2Details();
		
		if (cfg.WebServer.OAuth2 == null) {
			details.Provider = Setup.DEFAULT_PROVIDER;
			details.ClientId = Setup.DEFAULT_CLIENT_ID;
			details.ClientSecret = Setup.DEFAULT_CLIENT_SECRET;
		}
		else {
			details.Provider = cfg.WebServer.OAuth2.Provider;
			details.ClientId = cfg.WebServer.OAuth2.ClientId;
			details.ClientSecret = cfg.WebServer.OAuth2.ClientSecret;
			details.AuthURL = cfg.WebServer.OAuth2.AuthURL;
			details.TokenURL = cfg.WebServer.OAuth2.TokenURL;
			details.Scope = cfg.WebServer.OAuth2.Scope;
		}

		return(details);
	}

	public void setOAuth2Details(OAuth2Details details) {

		if (cfg.WebServer.OAuth2 == null) {
			cfg.WebServer.OAuth2 = new OAuth2Login.Config();
		}
		
		cfg.WebServer.OAuth2.Provider = details.Provider;
		cfg.WebServer.OAuth2.ClientId = details.ClientId;
		cfg.WebServer.OAuth2.ClientSecret = details.ClientSecret;
		cfg.WebServer.OAuth2.AuthURL = details.AuthURL;
		cfg.WebServer.OAuth2.TokenURL = details.TokenURL;
		cfg.WebServer.OAuth2.Scope = details.Scope;

		dirty = true;
	}

	// +----------+
	// | Dev Mode |
	// +----------+

	public void disableDevMode() {
		cfg.WebServer.AllowedOrigin = null;
		cfg.WebServer.OAuth2ForceState = null;
		dirty = true;
	}

	public void enableDevMode(String forceState) {
		cfg.WebServer.AllowedOrigin = "*";
		cfg.WebServer.OAuth2ForceState = forceState;
		dirty = true;
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
					case "exit": case "quit": case "q": if (confirmExit()) keepGoing = false; break;

					case "init":           init();               break;
					case "save":           save();               break;
					case "new_key":        newKey();             break;
					case "expire_rolled":  expireRolledKeys();   break;
					case "ssl":            ssl();                break;
					case "port":           port();               break;
					case "store":          store();              break;
					case "oauth2":         oauth2();             break;
					case "dev_mode":       dev_mode();           break;
					default:               tryShow(cmd);         break;
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
				  "  oauth2           Set authentication service \n" + 
				  "  dev_mode         Enable / disable developer mode \n" +            
				  "  show/? NODE      Show working config JSON subtree \n" +            
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

		// tryShow

		private void tryShow(String cmd) throws Exception {

			String params = cmd;
			boolean explicit = false;
			
			if (cmd.startsWith("show ") || cmd.startsWith("? ")) {
				explicit = true;
				params = params.substring(params.indexOf(" ")).trim();
			}

			try {
				String json = setup.getConfigSubTree(params);
				if (json == null) throw new Exception("huh?");
				print(json);
			}
			catch (Exception e) {
				print(explicit ? "node not found." : "huh?");
			}
		}
		
		// Init

		private void init() throws Exception {
			setup.newKey(true);
			store();
			port();
			ssl();
			oauth2();
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

		// OAuth2

		private void oauth2() throws Exception {

			print("You can set up any login provider that supports OAuth2 " +
				  "with OpenID Connect. Google, Facebook, GitHub and Amazon " +
				  "are supported by default; your enterprise login system (e.g., " +
				  "Microsoft Entra) almost certainly works as well. \n" +
				  " \n" +
				  "Your current redirect URL is " + setup.getOAuth2RedirectURL() + ". \n" +
				  " \n" +
				  "Which provider do you want to use? \n" +
				  "1. Google \n" +
				  "2. Facebook \n" +
				  "3. GitHub \n" +
				  "4. Amazon \n" +
				  "5. Other \n" +
				  "6. Use dev mode instead \n" +
				  " \n");

			String providerNum = prompt("your choice", "3");
			OAuth2Details details = new OAuth2Details();
			String docURL = "";

			switch (providerNum) {
				case "1":
					details.Provider = "google";
					docURL = "https://developers.google.com/identity/openid-connect/openid-connect";
					break;
					
				case "2":
					details.Provider = "facebook";
					docURL = "https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow";
					break;
					
				case "3":
					details.Provider = "github_reauth";
					docURL = "https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app";
					break;
					
				case "4":
					details.Provider = "amazon";
					docURL = "https://developer.amazon.com/docs/login-with-amazon/web-docs.html";
					break;
					
				case "5":
					details.Provider = "other";
					docURL = "";
					break;

				case "6":
					print("Dev mode is great for development, or trying out the app quickly, " +
						  "or if you really really really don't need any security or auditing " +
						  "at all. This last seems unlikely --- but we're all grownups here, right?");

					if (confirm("Are you sure", false)) dev_mode();
					else print("probably a good choice; aborting.");
					return;

				default:
					print("invalid choice; aborting.");
					return;
			}

			print("Use this URL to learn about setting up an OAuth2 web app for your provider:");
			print(docURL);

			
			details.ClientId = prompt("Application Client ID", "");
			details.ClientSecret = prompt("Application Client Secret", "");

			if (details.Provider.equals("other")) {
				details.AuthURL = prompt("Authentication URL:", "");
				details.TokenURL = prompt("Token URL:", "");
				details.Scope = prompt("Scope:", "openid email");
			}

			setup.setOAuth2Details(details);
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

		// Dev Mode

		private void dev_mode() {

			print("Dev mode allows you to run the client with 'npm start' and hit " +
				  "the server back end on a separate port. It also short-circuits " +
				  "OAuth2 authentication with a hard-coded token.");

			if (confirm("Enable dev mode", false)) {
				String token = prompt("Hard-coded OAuth2 token", DEFAULT_DEV_OAUTH2);
				setup.enableDevMode(token);
				print("enabled.");
			}
			else {
				setup.disableDevMode();
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
