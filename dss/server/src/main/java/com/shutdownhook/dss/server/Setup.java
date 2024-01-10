/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.dss.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.Encrypt;
import com.shutdownhook.toolbox.OAuth2Login;
import com.shutdownhook.toolbox.SimplePasswordStore;
import com.shutdownhook.toolbox.WebServer;

public class Setup
{
	private final static String DEFAULT_CONFIG = "config.json";
	private final static int DEFAULT_PORT = 3001;
	private final static String DEFAULT_STORE_LOC = "/tmp/dss.sql";

	private final static String DEFAULT_DEV_USER = "test|test@example.com|test_token";

	private final static String DEFAULT_AUTH_TYPE = WebServer.Config.AUTHTYPE_SIMPLE;
	
	private final static String DEFAULT_OAUTH2_PROVIDER = "github_reauth";
	private final static String DEFAULT_OAUTH2_CLIENT_ID = "CLIENT_ID";
	private final static String DEFAULT_OAUTH2_CLIENT_SECRET = "CLIENT_SECRET";
		
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

	// +---------------------+
	// | Authentication Type |
	// +---------------------+

	public String getAuthType() {
		return(cfg.WebServer.AuthenticationType == null
			   ? DEFAULT_AUTH_TYPE : cfg.WebServer.AuthenticationType);
	}

	public void setAuthType(String authType) {
		cfg.WebServer.AuthenticationType = authType;
		dirty = true;
	}
	
	// +-------------+
	// | Simple auth |
	// +-------------+

	public SimplePasswordStore getSimplePasswordStore() {
		
		return(cfg.WebServer.SimplePasswordStore == null
			   ? new SimplePasswordStore()
			   : new SimplePasswordStore(cfg.WebServer.SimplePasswordStore));
	}
	
	public void setSimplePasswordStore(SimplePasswordStore store) {
		cfg.WebServer.SimplePasswordStore = store.getConfig();
		dirty = true;
	}

	public List<String> getSimpleUsers() {

		List<String> users = new ArrayList<String>();
		
		SimplePasswordStore store = getSimplePasswordStore();
		for (SimplePasswordStore.Credential cred : store.getConfig().Credentials) {
			users.add(cred.User);
		}

		return(users);
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
			details.Provider = Setup.DEFAULT_OAUTH2_PROVIDER;
			details.ClientId = Setup.DEFAULT_OAUTH2_CLIENT_ID;
			details.ClientSecret = Setup.DEFAULT_OAUTH2_CLIENT_SECRET;
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

	// +-------------+
	// | Forced Auth |
	// +-------------+

	public void setForcedAuth(String forceState) {

		WebServer.LoggedInUser user = new WebServer.LoggedInUser();
		String[] fields = forceState.split("\\|");
		if (fields.length >= 1) user.Id = fields[0];
		if (fields.length >= 2) user.Email = fields[1];
		if (fields.length >= 3) user.Token = fields[2];
		
		cfg.WebServer.AllowedOrigin = "*";
		cfg.WebServer.ForceLoggedInUser = user;
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
					case "simple_upsert":  simple_upsert();      break;
					case "simple_delete":  simple_delete();      break;
					case "simple_list":    simple_list();        break;
					case "dev_mode_on":    dev_mode_on();        break;
					case "dev_mode_off":   dev_mode_off();       break;
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
				  "  oauth2           Configure OAUTH2 authentication service \n" + 
				  "  simple_upsert    Add/update Simple auth user \n" + 
				  "  simple_delete    Delete Simple auth user \n" + 
				  "  simple_list      List Simple auth users \n" + 
				  "  dev_mode_on      Enable developer mode auth \n" +
				  "  dev_mode_off     Disable developer mode auth \n" +
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

			print("SQL Hammer requires one of following authentication types. You can change this \n " +
				  "later with the setup.sh script, so don't panic. \n " +
				  "\n " +
				  "1. SIMPLE - a list of users with hashed passwords stored directly in your \n " +
				  "            configuration file. Add users with the 'simple_upsert' command. \n " +
				  "2. OAUTH2 - use an external authentication provider including Google, \n " +
				  "            Facebook, Github, Amazon or an Enterprise SSO provider like \n " +
				  "            Microsoft Entra or Okta. \n " +
				  "3. FORCE  - hardcode a single account used for all access to SQL Hammer. This \n " +
				  "            is also called 'dev mode' and is probably a terrible idea for your \n " +
				  "            installation. But we're all adults here. \n");

			String choice = "";
			while (choice.equals("")) {
				
				choice = prompt("Which model would you like to use", "1");
				switch (choice) {
					case "1":
						print("Add your first user to get started.");
						simple_upsert();
						break;

					case "2":
						oauth2();
						break;

					case "3":
						dev_mode_on();
						break;

					default:
						choice = "";
						break;
				}
			}
				  
			save();
		}

		// Store

		private void store() throws Exception {

			String currentLoc = setup.getStoreConnectionString();
			if (currentLoc != null) currentLoc = setup.getStoreConnectionString().replace("jdbc:sqlite:","");
			if (currentLoc == null) currentLoc = Setup.DEFAULT_STORE_LOC;
			
			String newLoc = prompt("location for metadata file", currentLoc);

			setup.setStoreConnectionString("jdbc:sqlite:" + newLoc);
			print("updated.");
		}

		// Simple Auth

		private void simple_upsert() {

			String user = prompt("username to add or update","");
			if (user.isEmpty()) { print("cancelled."); return; }

			String pass = prompt("password","");
			if (pass.isEmpty()) { print("cancelled."); return; }

			SimplePasswordStore store = setup.getSimplePasswordStore();
			store.upsert(user, pass);

			setup.setSimplePasswordStore(store);
			setup.setAuthType(WebServer.Config.AUTHTYPE_SIMPLE);
			print("updated.");
		}

		private void simple_delete() {
			
			String user = prompt("username to remove","");
			if (user.isEmpty()) { print("cancelled."); return; }

			SimplePasswordStore store = setup.getSimplePasswordStore();
			store.delete(user);

			setup.setSimplePasswordStore(store);
			setup.setAuthType(WebServer.Config.AUTHTYPE_SIMPLE);
			print("updated.");
		}

		private void simple_list() {
			List<String> users = setup.getSimpleUsers();
			print(String.format("%d user%s configured", users.size(), users.size() == 1 ? "" : "s"));
			for (String user: users) print("* " + user);
		}
			
		// OAuth2

		private void oauth2() throws Exception {

			OAuth2Details details = setup.getOAuth2Details();
			
			print("You can set up any login provider that supports OAuth2 " +
				  "with OpenID Connect. Google, Facebook, GitHub and Amazon " +
				  "are supported by default; your enterprise login system (e.g., " +
				  "Microsoft Entra) almost certainly works as well. \n" +
				  " \n" +
				  "Your current redirect URL is " + setup.getOAuth2RedirectURL() + ". \n" +
				  " \n" +
				  "Which provider do you want to use? \n");

			for (int i = 0; i < OAUTH2_PROVIDER_LIST.length; ++i) {
				print(String.format("%d. %s", i+1, OAUTH2_PROVIDER_LIST[i]));
			}

			print(String.format("%d. Cancel", OAUTH2_PROVIDER_LIST.length + 1));

			String defaultProvider = Integer.toString(indexFromProvider(details.Provider) + 1);
			String providerStr = prompt("your choice", defaultProvider);

			Integer newProviderIndex = indexFromProviderPick(providerStr);
			if (newProviderIndex == null) {
				print("cancelled.");
				return;
			}
			
			String docURL = OAUTH2_DOC_URLS[newProviderIndex];
			if (docURL != null) {
				print("Use this URL to learn about setting up an OAuth2 web app for your provider:");
				print(docURL);
			}

			details.Provider = OAUTH2_PROVIDER_LIST[newProviderIndex];
			details.ClientId = prompt("Application Client ID", details.ClientId);
			details.ClientSecret = prompt("Application Client Secret", details.ClientSecret);

			if (details.Provider.equals("other")) {
				details.AuthURL = prompt("Authentication URL:", "");
				details.TokenURL = prompt("Token URL:", "");
				details.Scope = prompt("Scope:", "openid email");
			}

			setup.setOAuth2Details(details);
			setup.setAuthType(WebServer.Config.AUTHTYPE_OAUTH2);
			print("updated.");
		}

		private static int indexFromProvider(String provider) {

			int i = 0;
			while (i < OAUTH2_PROVIDER_LIST.length) {
				if (OAUTH2_PROVIDER_LIST[i].equalsIgnoreCase(provider)) break;
				++i;
			}

			return(i);
		}

		private static Integer indexFromProviderPick(String num) {

			try {
				int i = Integer.parseInt(num);
				if (i <= 0 || i > OAUTH2_PROVIDER_LIST.length) return(null);
				return(i - 1);
			}
			catch (Exception e) {
				return(null);
			}
		}
		
		private final static String[] OAUTH2_PROVIDER_LIST = {
			OAuth2Login.PROVIDER_GOOGLE,
			OAuth2Login.PROVIDER_FACEBOOK,
			OAuth2Login.PROVIDER_GITHUB,
			OAuth2Login.PROVIDER_GITHUB_REAUTH,
			OAuth2Login.PROVIDER_AMAZON,
			OAuth2Login.PROVIDER_OTHER
		};
	
		private final static String OAUTH2_DOC_URLS[] = {
			"https://developers.google.com/identity/openid-connect/openid-connect",
			"https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow",
			"https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app",
			"https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app",
			"https://developer.amazon.com/docs/login-with-amazon/web-docs.html",
			null
		};

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

		private void dev_mode_on() {

			print("Dev mode allows you to run the client with 'npm start' and hit " +
				  "the server back end on a separate port. It also short-circuits " +
				  "authentication with a hard-coded token.");

			if (confirm("Enable dev mode", false)) {
				String token = prompt("Hard-coded user token", DEFAULT_DEV_USER);
				setup.setForcedAuth(token);
				setup.setAuthType(WebServer.Config.AUTHTYPE_FORCE);
				print("enabled.");
			}
			else {
				print("cancelled.");
			}
		}
		
		private void dev_mode_off() {
			List<String> simpleUsers = setup.getSimpleUsers();
			if (simpleUsers.size() > 0) {
				print("resetting to simple auth");
				setup.setAuthType(WebServer.Config.AUTHTYPE_SIMPLE);
			}
			else {
				print("resetting to OAuth2");
				setup.setAuthType(WebServer.Config.AUTHTYPE_OAUTH2);
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
