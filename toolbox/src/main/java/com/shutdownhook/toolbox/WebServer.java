/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.lang.InterruptedException;
import java.lang.Runtime;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WebServer implements Closeable
{
	// +---------------+
	// | Configuration |
	// +---------------+
	
	public static class Config
	{
		// +-----------
		// | Connection
		
		public int Port = 7071;

		// if these are set, will create a SecureServer instead of
		// a regular one. Parameters are named to match Apache config values
		// so hopefully they make sense to people.
		public String SSLCertificateFile;
		public String SSLCertificateKeyFile;

		// +-----
		// | CORS

		// If AllowedOrigin is non-empty, these are used to support CORS
		public String AllowedOrigin;
		public String AllowedMethods = "GET,PUT,POST,OPTIONS";
		public String AllowedHeaders = "Content-Type,Authorization";
		
		// +---------------
		// | Authentication
		
		// Used to pick an authentication type. null / not present = None.
		// Authentication is applied to all routes equivalently.
		// Look below for per-type configuration details
		public String AuthenticationType;
		public String LogoutPath = "/__logout";

		public final static String AUTHTYPE_NONE = "none";
		public final static String AUTHTYPE_FORCE = "force"; 
		public final static String AUTHTYPE_BASIC = "basic"; 
		public final static String AUTHTYPE_SIMPLE = "simple"; 
		public final static String AUTHTYPE_OAUTH2 = "oauth2"; 
		public final static String AUTHTYPE_XMS = "xms"; // Azure "Easy Auth"

		// * AUTHTYPE_FORCE
		// Value will be used as fixed auth state. TEST/DEV ONLY!
		public LoggedInUser ForceLoggedInUser;

		// * AUTHTYPE_BASIC
		// Basic authentication using a provided PasswordStore implementation.
		// "BasicAuthConfiguration" is provided to the PasswordStore via init()
		public String BasicAuthClassName;
		public String BasicAuthConfiguration;
		public String BasicAuthCookieName = "SHOOK_BASIC";
		public String BasicAuthRealm = "Site Access";
		public String Basic401Path = "/__401";

		// * AUTHTYPE_SIMPLE
		// Basic authentication using user/pass combos stored in config.
		// Credential information can be added/removed from config using the methods
		// in SimplePasswordStore.java. Only one of the two values below
		// shoudl be used; in-line config takes precedence.
		public SimplePasswordStore.Config SimplePasswordStore;
		public String SimplePasswordStorePath;

		// * AUTHTYPE_OAUTH2
		// if non-empty, the provided config will be used to protect all routes
		public OAuth2Login.Config OAuth2;
		public String OAuth2CookieName = "SHOOK_OAUTH2";

		// +-------------
		// | Static Pages
		
		// optional directory holding static pages. Each file with an extension
		// found in StaticPagesExtensionMap will be exposed as a static route.
		// Subdirectories are included. We use a whitelist here to provide a bit
		// of protection from accidentally including unwanted files.
		public String StaticPagesDirectory;

		// Same as above, but the content is in a zip file that can be on the
		// file system or in a resource (prefixed by '@'). Content will be extracted
		// to a temp directory, served from there, and deleted on exit.
		public String StaticPagesZip;

		public Boolean StaticPagesRouteHtmlWithoutExtension = true;
		public String StaticPagesIndexFile = "index.html";
		
		public Map<String,String> StaticPagesExtensionMap = Map.ofEntries(
			  Map.entry("txt", "text/plain"),
			  Map.entry("html", "text/html"),
			  Map.entry("htm", "text/html"),
			  Map.entry("xml", "application/xml"),
			  Map.entry("xsl", "application/xml"),
			  Map.entry("xsd", "application/xml"),
			  Map.entry("js", "text/javascript"),
			  Map.entry("css", "text/css"),
			  Map.entry("json", "application/json"),
			  Map.entry("map", "application/json"),
			  Map.entry("jpg", "image/jpeg"),
			  Map.entry("jpeg", "image/jpeg"),
			  Map.entry("png", "image/x-png"),
			  Map.entry("ico", "image/x-icon"),
			  Map.entry("heic", "image/heic"),
			  Map.entry("webp", "image/webp"),
			  Map.entry("woff", "font/woff"),
			  Map.entry("woff2", "font/woff2")
		);
		
		// +--------------
		// | Miscellaneous
		
		public int ThreadCount = 0; // 0 = use an on-demand pool, else specific fixed count
		public int ShutdownWaitSeconds = 30;
		public boolean ReturnExceptionDetails = false;
		public boolean ReadBodyAsString = true;

		// these aren't documented and are a bit problematic because they
		// get set as statics ... if you set them you'll change all instances
		// in the process. But without them clients can end up hanging sockets
		// for a long time / forever. Use 0 to leave system defaults untouched;
		// -1 for infinite; else seconds. 
		public int MaxReadSeconds = (60 * 3);
		public int MaxWriteSeconds = (60 * 3);

		// If non-empty, will be used to encrypt / decrypt session cookies automatically
		public Encrypt.Config CookieEncrypt;

	}

	// +---------------------------+
	// | Handler - Implement These |
	// +---------------------------+

	public interface Handler {
		public void handle(Request request, Response response) throws Exception;
	}

	// +---------+
	// | Request |
	// +---------+

	public static class Request {
		
		public Request(Encrypt cookieEncrypt) { this.cookieEncrypt = cookieEncrypt; }
		private Encrypt cookieEncrypt;
		
		public String Base;
		public String Path;
		public String Method;
		public String RemoteAddress;
		public String Referrer;
		public boolean Secure;
		public String QueryString;
		public Map<String,String> QueryParams;
		public Map<String,String> Cookies;
		public Map<String,List<String>> Headers;
		public String Body;
		public InputStream BodyStream;
		public LoggedInUser User; // only if logged in

		public Map<String,String> parseBodyAsQueryString() {
			return(Easy.parseQueryString(Body));
		}

		public String getHeader(String name) {
			List<String> hdrs = Headers.get(name);
			if (hdrs == null || hdrs.size() == 0) return(null);
			return(hdrs.get(0));
		}

		private InputStream InnerBodyStream;
	}

	public static class LoggedInUser
	{
		public String Id;
		public String Email;
		public String Token;
		public Map<String,String> Properties;
	}

	// +----------+
	// | Response |
	// +----------+

	public static class Response {

		public Response(Encrypt cookieEncrypt) { this.cookieEncrypt = cookieEncrypt; }
		private Encrypt cookieEncrypt;
		
		public int Status;
		public String Body;
		public File BodyFile;
		public Boolean DeleteBodyFile;
		public String ContentType;
		public Map<String,String> Headers;
		public Map<String,String> Cookies;

		public void setJson(String s) { Status = 200; Body = s; ContentType = "application/json"; }
		public void setText(String s) { Status = 200; Body = s; ContentType = "text/plain"; }
		public void setHtml(String s) { Status = 200; Body = s; ContentType = "text/html"; }
		public void setJS(String s) { Status = 200; Body = s; ContentType = "text/javascript"; }

		public void redirect(String url) {
			Status = 302;
			addHeader("Location", url);
		}

		public void setSessionCookie(String name, String val, Request request) {

			String valX = (cookieEncrypt == null ? val : cookieEncrypt.encrypt(val));
				
			String cookie = name + "=" + Easy.urlEncode(valX) + "; HttpOnly";
			if (request.Secure) cookie = cookie + "; Secure; SameSite=None";

			if (Cookies == null) Cookies = new HashMap<String,String>();
			Cookies.put(name, cookie);
		}
		
		public void deleteSessionCookie(String name, Request request) {

			String cookie = name + "=; HttpOnly";
			if (request.Secure) cookie = cookie + "; Secure; SameSite=None";
			cookie = cookie + "; expires=Thu, 01 Jan 1970 00:00:00 GMT";

			if (Cookies == null) Cookies = new HashMap<String,String>();
			Cookies.put(name, cookie);
		}

		public void addHeader(String name, String val) {
			if (Headers == null) Headers = new HashMap<String,String>();
			Headers.put(name, val);
		}
	}

	// +---------------+
	// | PasswordStore |
	// +---------------+

	public interface PasswordStore {
		
		default public boolean init(String param) { return(true); }
		public boolean check(String user, String password);
		default public Map<String,String> getProperties(String user) { return(null); }
	}

	// +------------------+
	// | WebServer Public |
	// +------------------+

	public static WebServer create(Config cfg)
		throws Exception {

		WebServer server = (cfg.SSLCertificateFile == null ?
							new WebServer(cfg) : new SecureServer(cfg));

		if (cfg.MaxReadSeconds != 0) {
			System.setProperty("sun.net.httpserver.maxReqTime", Long.toString(cfg.MaxReadSeconds));
		}
		
		if (cfg.MaxWriteSeconds != 0) {
			System.setProperty("sun.net.httpserver.maxRspTime", Long.toString(cfg.MaxWriteSeconds));
		}

		server.createHttpServer(new InetSocketAddress(cfg.Port));
		server.setExecutor();

		server.registerStaticRoutes();

		if (cfg.CookieEncrypt != null) server.cookieEncrypt = new Encrypt(cfg.CookieEncrypt);

		server.registerAuth();

		return(server);
	}

	public void start() {
		server.start();
		log.info("WebServer listening for requests on port " + Integer.toString(cfg.Port));
	}
	
	public void close() {

		log.info("WebServer Shutting down");

		if (oauth2 != null) oauth2.close();
		
		server.stop(0);

		try {
			pool.shutdown();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
			pool.shutdownNow();
			pool.awaitTermination(cfg.ShutdownWaitSeconds / 2, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			// eat it
		}

		if (staticPagesTemp != null) {
			try { Easy.recursiveDelete(staticPagesTemp); }
			catch (Exception e) { /* eat it */ }
			staticPagesTemp = null;
		}
	}

	public void runSync() {

		final Object shutdownTrigger = new Object();
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				close();
			}
			catch (Exception e) {
				// nothihg
			}
			finally {
				synchronized (shutdownTrigger) { shutdownTrigger.notify(); }
			}
		}));

		start();
		
		log.info("Listening for requests; ^C to exit");

		synchronized (shutdownTrigger) {
			try { shutdownTrigger.wait(); }
			catch (InterruptedException e) { /* nut-n-honey */ }
		}
	}

	public void registerStaticHandler(String urlPrefix,
									  final String body,
									  final String contentType) {

		registerHandler(urlPrefix, new Handler() {
			public void handle(Request request, Response response) throws Exception {
				response.ContentType = contentType;
				response.Body = body;
			}
		});
	}

	public void registerFileHandler(String urlPrefix,
									final File file,
									final String contentType) {

		registerHandler(urlPrefix, new Handler() {
			public void handle(Request request, Response response) throws Exception {
				response.ContentType = contentType;
				response.BodyFile = file;
			}
		});
	}

	public void registerEmptyHandler(String urlPrefix,
									 final int status) {

		registerHandler(urlPrefix, new Handler() {
			public void handle(Request request, Response response) throws Exception {
				response.Status = status;
				response.ContentType = "text/plain";
				response.Body = "";
			}
		});
	}

	public void registerHandler(String urlPrefix, final Handler handler) {

		server.createContext(urlPrefix, new HttpHandler() {
				
			public void handle(HttpExchange exchange) {
				
				log.info(String.format("WebServer %s: %s (%s)",
									   handler.getClass().getName(),
									   exchange.getRequestURI(),
									   exchange.getRequestMethod()));

				Response response = new Response(cookieEncrypt);
				response.Status = 200; // optimistic!

				Request request = null;
				
				try {
					request = setupRequest(exchange);
					
					if (!handlePreflight(request, response) &&
						!shortCircuitForAuth(request, response)) {
						
						handler.handle(request, response);
					}
				}
				catch (Exception e) {
					
					response.Status = 500;
					response.Body = null;
					response.ContentType = null;

					String msg = Easy.exMsg(e, handler.getClass().getName(), true);
					log.severe(msg);

					if (cfg.ReturnExceptionDetails) {
						response.Body = msg;
						response.ContentType = "text/plain";
					}
				}
				finally {

					if (request != null) {
						if (request.BodyStream != null) Easy.safeClose(request.BodyStream);
						if (request.InnerBodyStream != null) Easy.safeClose(request.InnerBodyStream);
					}
					
					try { sendResponse(exchange, response); }
					catch (IOException inner) { /* oh well */ }
					
					exchange.close();
				}
			}
		});
	}

	// +-------------------+
	// | WebServer Private |
	// +-------------------+

	public WebServer(Config cfg) throws Exception {
		this.cfg = cfg;
		this.jsonParser = new JsonParser();
	}

	protected void createHttpServer(InetSocketAddress address) throws Exception {
		server = HttpServer.create(new InetSocketAddress(cfg.Port), 0);
	}
	
	private void setExecutor() {

		pool = (cfg.ThreadCount == 0
				? Executors.newCachedThreadPool()
				: Executors.newFixedThreadPool(cfg.ThreadCount));

		server.setExecutor(pool);
	}
	
	private void sendResponse(HttpExchange exchange, Response response) throws IOException {

		handleCORS(response);
		
		if (response.Headers != null) {
			for (String name : response.Headers.keySet()) {
				exchange.getResponseHeaders().add(name, response.Headers.get(name));
			}
		}

		if (response.Cookies != null) {
			for (String name : response.Cookies.keySet()) {
				exchange.getResponseHeaders().add("Set-Cookie", response.Cookies.get(name));
			}
		}

		if (response.ContentType != null) {
			exchange.getResponseHeaders().add("Content-Type", response.ContentType);
		}

		if (response.BodyFile != null) {
			exchange.sendResponseHeaders(response.Status, response.BodyFile.length());
			sendFileTo(response.BodyFile, exchange.getResponseBody());
			if (response.DeleteBodyFile != null && response.DeleteBodyFile == true) {
				try { response.BodyFile.delete(); }
				catch (Exception de) { /* eat it */ }
			}
		}
		else if (response.Body == null || response.Body.isEmpty()) {
			exchange.sendResponseHeaders(response.Status, -1);
		}
		else {
			byte[] rgb = response.Body.getBytes();
			exchange.sendResponseHeaders(response.Status, rgb.length);
			exchange.getResponseBody().write(rgb);
		}
	}

	private Request setupRequest(HttpExchange exchange) throws IOException, IllegalArgumentException {

		Request request = new Request(cookieEncrypt);

		setBaseUrl(exchange, request);
		
		request.Path = exchange.getRequestURI().toString();
		request.Method = exchange.getRequestMethod();
		request.RemoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
		request.Referrer = exchange.getRequestHeaders().getFirst("Referer");
		request.Headers = exchange.getRequestHeaders();

		// query string
		request.QueryString = exchange.getRequestURI().getRawQuery();
		request.QueryParams = Easy.parseQueryString(request.QueryString);

		// cookies
		request.Cookies = new HashMap<String,String>();
		List<String> cookies = exchange.getRequestHeaders().get("Cookie");

		if (cookies != null) {
			for (String cookieList : cookies) {
				for (String cookie : cookieList.split(";")) {
					String[] nv = cookie.split("=");
					if (nv.length != 2) continue;
					try {
						String val = Easy.urlDecode(nv[1].trim());
						if (cookieEncrypt != null) val = cookieEncrypt.decrypt(val);
						request.Cookies.put(nv[0].trim(), val);
					} catch (Exception e) {
						// we encode all of ours so just ignore this
					}
				}
			}
		}
		
		// body
		if (request.Method.equalsIgnoreCase("POST") ||
			request.Method.equalsIgnoreCase("PUT")) {

			if (cfg.ReadBodyAsString) {
				
				InputStream stream = null;

				try {
					stream = exchange.getRequestBody();
					request.Body = Easy.stringFromInputStream(stream);
				}
				finally {
					if (stream != null) stream.close();
				}
			}
			else {
				// will be closed by registerHandler finally clause
				request.BodyStream = exchange.getRequestBody();
				
				List<String> contentTypeHeaders = request.Headers.get("Content-Type");
				String ct = ((contentTypeHeaders != null && contentTypeHeaders.size() >= 1)
							 ? contentTypeHeaders.get(0).toLowerCase() : "");

				if (ct.equals("application/zip")) {
					request.InnerBodyStream = request.BodyStream;
					request.BodyStream = new ZipInputStream(request.InnerBodyStream);
					((ZipInputStream)request.BodyStream).getNextEntry();
				}
				else if (ct.equals("application/gzip") || ct.equals("application/x-gzip")) {
					request.InnerBodyStream = request.BodyStream;
					request.BodyStream = new GZIPInputStream(request.InnerBodyStream);
				}
			}
		}

		return(request);
	}

	private void setBaseUrl(HttpExchange exchange, Request request) {

		request.Secure = (this instanceof SecureServer);

		String hostAndPort = exchange.getRequestHeaders().getFirst("Host");
		
		if (hostAndPort == null) {
			// this is fallback for really lame clients that don't send Host.
			// are there really any of those out there besides me using telnet?
			int port = server.getAddress().getPort();
			String portStr = (((request.Secure && port == 443) || (!request.Secure && port == 80))
							  ? "" : ":" + Integer.toString(port));

			hostAndPort = server.getAddress().getHostName() + portStr;
		}

		request.Base = "http" + (request.Secure ? "s" : "") + "://" + hostAndPort;
	}

	private void registerStaticRoutes() throws Exception {
		
		if (cfg.StaticPagesDirectory != null) {

			log.info(String.format("Registering pages from %s", cfg.StaticPagesDirectory));

			registerStaticRoutesHelper(new File(cfg.StaticPagesDirectory), "/");
		}

		if (cfg.StaticPagesZip != null) {

			staticPagesTemp = Files.createTempDirectory("shweb").toFile();
			String zipPath = new File(staticPagesTemp, "pages.zip").getAbsolutePath();
			Easy.smartyPathToFile(cfg.StaticPagesZip, zipPath);
			Easy.unzipToPath(zipPath, staticPagesTemp.getAbsolutePath());
			
			log.info(String.format("Registering pages from %s (%s)",
								   staticPagesTemp.getAbsolutePath(),
								   cfg.StaticPagesZip));
								   
			registerStaticRoutesHelper(staticPagesTemp, "/");
		}
	}
	
	private void registerStaticRoutesHelper(File dir, String prefix) throws Exception {
		
		for (File file : dir.listFiles()) {

			String name = file.getName();

			if (file.isDirectory()) {
				registerStaticRoutesHelper(file, prefix + name + "/");
				continue;
			}
			
			int ichLastDot = name.lastIndexOf(".");
			if (ichLastDot == -1) continue;

			String ext = name.substring(ichLastDot + 1).toLowerCase();

			if (cfg.StaticPagesExtensionMap.containsKey(ext)) {

				String route =
					(cfg.StaticPagesRouteHtmlWithoutExtension && ext.equals("html")
					 ? name.substring(0, ichLastDot) : name);

				String contentType = cfg.StaticPagesExtensionMap.get(ext);
				registerFileHandler(prefix + route, file, contentType);

				if (name.equals(cfg.StaticPagesIndexFile)) {
					registerFileHandler(prefix, file, contentType);
				}
			}
		}
	}

	// +--------------+
	// | registerAuth |
	// +--------------+

	private void registerAuth() throws Exception {

		if (cfg.AuthenticationType == null) return;
		
		switch (cfg.AuthenticationType.toLowerCase()) {
			
			case Config.AUTHTYPE_BASIC:
				registerPasswordStore();
				break;

			case Config.AUTHTYPE_SIMPLE:
				registerSimplePasswordStore();
				break;

			case Config.AUTHTYPE_OAUTH2:
				registerOAuth2();
				break;

			case Config.AUTHTYPE_NONE:
			case Config.AUTHTYPE_XMS:
				return;
				
			case Config.AUTHTYPE_FORCE:
				log.warning("USING FORCED LOGGEDINUSER --- this should never show in prod!");
				return;

			default:
				throw new Exception("Unknown AuthenticationType: " + cfg.AuthenticationType);
		}

		registerLogoutHandler();
	}

	private void registerPasswordStore() throws Exception {

		passwordStore = (PasswordStore) Class.forName(cfg.BasicAuthClassName).newInstance();
		
		if (!passwordStore.init(cfg.BasicAuthConfiguration)) {
			throw new Exception("Password Store init failed for " + cfg.BasicAuthClassName);
		}
			
		log.info("Configuring Basic authenication with " + cfg.BasicAuthClassName);
	}

	private void registerSimplePasswordStore() throws Exception {

		if (cfg.SimplePasswordStore != null) {
			
			if (cfg.SimplePasswordStorePath != null) {

				log.warning("Can't have both SimplePasswordStore and ...Path " +
							"set in config; preferring inline.");
			}
			
			passwordStore = new SimplePasswordStore(cfg.SimplePasswordStore);
		}
		else if (cfg.SimplePasswordStorePath != null) {

			String jsonSPS = Easy.stringFromFile(cfg.SimplePasswordStorePath);
			SimplePasswordStore.Config cfgSPS = new Gson().fromJson(jsonSPS, SimplePasswordStore.Config.class);
			passwordStore = new SimplePasswordStore(cfgSPS);
		}
		else {
			throw new Exception("Simple authtype selected but no config!");
		}
		
		log.info("Configuring Simple Basic authenication");
	}

	private void registerOAuth2() throws Exception {

		oauth2 = new OAuth2Login(cfg.OAuth2);

		log.info("Configuring OAuth2 authentication (" + cfg.OAuth2.RedirectPath + ")");
		
		registerHandler(cfg.OAuth2.RedirectPath, new Handler() {
			public void handle(Request request, Response response) throws Exception {

				OAuth2Login.State state = getOAuth2State(request);
				String error = oauth2.handleReturnURL(request.Base, request.Path, state);

				if (error != null) {
					response.Status = 502;
					response.Body = error;
					return;
				}
				
				String targetURL = state.popTransitoryRedirect();
				response.setSessionCookie(cfg.OAuth2CookieName, state.dehydrate(), request);

				response.redirect(targetURL);
			}
		});
	}

	private void registerLogoutHandler() throws Exception {

		String authType = cfg.AuthenticationType.toLowerCase();
		
		final boolean activeLogout =
			(Config.AUTHTYPE_BASIC.equals(authType) || Config.AUTHTYPE_SIMPLE.equals(authType))
			? true : false;

		String templateText = Easy.stringFromResource("logout.html.tmpl");
		final Template template = new Template(templateText);
		
		registerHandler(cfg.LogoutPath, new Handler() {
				
			public void handle(Request request, Response response) throws Exception {

				response.deleteSessionCookie(cfg.BasicAuthCookieName, request);
				response.deleteSessionCookie(cfg.OAuth2CookieName, request);
				
				String redir = request.QueryParams.get("r");
				if (Easy.nullOrEmpty(redir) || redir.indexOf("://") != -1) redir = "/";

				HashMap tokens = new HashMap<String,String>();
				tokens.put("401_URL", cfg.Basic401Path);
				tokens.put("REDIRECT_URL", redir);
				tokens.put("ACTIVE_LOGOUT", activeLogout ? "TRUE" : "FALSE");
				
				response.setHtml(template.render(tokens));
			}

		});
		
		if (activeLogout) {

			registerHandler(cfg.Basic401Path, new Handler() {
				public void handle(Request request, Response response) throws Exception {
					response.Status = 401;
				}
			});
		}
		
	}

	// +---------------------+
	// | shortCircuitForAuth |
	// +---------------------+
	
	private boolean shortCircuitForAuth(Request request, Response response) {

		if (cfg.AuthenticationType == null) return(false);
		if (request.Path.startsWith(cfg.LogoutPath)) return(false);
		
		switch (cfg.AuthenticationType.toLowerCase()) {
			
			case Config.AUTHTYPE_BASIC:
			case Config.AUTHTYPE_SIMPLE:
				return(shortCircuitForPasswordStore(request, response));

			case Config.AUTHTYPE_OAUTH2:
				return(shortCircuitForOAuth2(request, response));

			case Config.AUTHTYPE_XMS:
				return(shortCircuitForXMS(request, response));

			case Config.AUTHTYPE_NONE:
				return(false);
				
			case Config.AUTHTYPE_FORCE:
				request.User = cfg.ForceLoggedInUser;
				return(false);
		}

		return(false);
	}
	
	// PasswordStore

	private boolean shortCircuitForPasswordStore(Request request, Response response) {

		// already logged in?

		String user = request.Cookies.get(cfg.BasicAuthCookieName);

		if (!Easy.nullOrEmpty(user)) {
			request.User = new LoggedInUser();
			request.User.Id = user;
			request.User.Properties = passwordStore.getProperties(user);
			return(false);
		}

		// nope, check for header

		String authInfo = null;
		List<String> authHeaders = request.Headers.get("Authorization");
		if (authHeaders != null && authHeaders.size() >= 1) {
			String authHeader = authHeaders.get(0);
			if (authHeader.startsWith("Basic ")) {
				String authBase64 = authHeader.substring(6).trim(); // skip the basic part
				authInfo = Easy.base64Decode(authBase64);
			}
		}

		if (authInfo != null) {
			
			int ichUser = authInfo.indexOf(":");
			String authUser = authInfo.substring(0, ichUser);
			String authPass = authInfo.substring(ichUser + 1);

			if (passwordStore.check(authUser, authPass)) {
				response.setSessionCookie(cfg.BasicAuthCookieName, authUser, request);
				request.User = new LoggedInUser();
				request.User.Id = authUser;
				request.User.Properties = passwordStore.getProperties(authUser);
				return(false);
			}
		}

		// either no header or it's bogus; authenticate!

		response.Status = 401;
		
		String hdr = String.format("Basic realm=\"%s\"", cfg.BasicAuthRealm);
		response.addHeader("WWW-Authenticate", hdr);

		return(true);
	}

	// OAuth2

	private boolean shortCircuitForOAuth2(Request request, Response response) {

		// quick exit

		if (request.Path.startsWith(cfg.OAuth2.RedirectPath)) return(false);
		
		// already logged in?
		
		OAuth2Login.State state = getOAuth2State(request);
		
		if (state.isAuthenticated()) {
			
			request.User = new LoggedInUser();
			request.User.Id = state.getId();
			request.User.Email = state.getEmail();
			request.User.Token = state.getToken();
			
			return(false);
		}

		// ok, fine ... redirect to auth url
		String url = oauth2.getAuthenticationURL(request.Base, request.Path, state);
		response.setSessionCookie(cfg.OAuth2CookieName, state.dehydrate(), request);
		response.redirect(url);

		return(true);
	}

	private OAuth2Login.State getOAuth2State(Request request) {
		String dehydrated = request.Cookies.get(cfg.OAuth2CookieName);
		return(OAuth2Login.State.rehydrate(dehydrated));
	}

	// XMS (Azure Easy Auth)

	private boolean shortCircuitForXMS(Request request, Response response) {

		String idToken = request.getHeader("X-MS-TOKEN-AAD-ID-TOKEN");

		if (idToken == null) {
			log.warning("If XMS auth is configured, shouldn't be here without X-MS-AAD-ID-TOKEN!");
			response.Status = 401;
			return(true);
		}
		
		String payloadEnc = idToken.split("\\.")[1];
		String payloadTxt = Easy.base64urlDecode(payloadEnc);
		JsonObject jsonIdToken = jsonParser.parse(payloadTxt).getAsJsonObject();
		log.fine("ID_TOKEN: " + payloadTxt);

		request.User = new LoggedInUser();
		request.User.Id = jsonIdToken.get("sub").getAsString();
		if (jsonHasNonNull(jsonIdToken, "email")) request.User.Email = jsonIdToken.get("email").getAsString();

		// this may need tweaking as we get more usage
		request.User.Properties = new HashMap<String,String>();
		addUserProperty(request.User.Properties, jsonIdToken, "aud");
		addUserProperty(request.User.Properties, jsonIdToken, "tid");
		addUserProperty(request.User.Properties, jsonIdToken, "name");
		addUserProperty(request.User.Properties, jsonIdToken, "preferred_username");
		if (jsonHasNonNull(jsonIdToken, "roles")) {
			JsonArray roles = jsonIdToken.get("roles").getAsJsonArray();
			for (int i = 0; i < roles.size(); ++i) {
				request.User.Properties.put(roles.get(i).getAsString(), "true");
			}
		}
		
		request.User.Token = request.getHeader("X-MS-TOKEN-AAD-ACCESS-TOKEN");
		
		return(false);
	}

	private void addUserProperty(Map<String,String> props, JsonObject jsonIdToken, String name) {
		if (!jsonHasNonNull(jsonIdToken, name)) return;
		props.put(name, jsonIdToken.get(name).getAsString());
	}

	private boolean jsonHasNonNull(JsonObject json, String field) {
		if (!json.has(field)) return(false);
		if (json.get(field) == null) return(false);
		if (json.get(field).isJsonNull()) return(false);
		return(true);
	}

	// +------+
	// | CORS |
	// +------+

	private boolean handlePreflight(Request request, Response response) {
		
		if (cfg.AllowedOrigin == null) return(false);
		if (!request.Method.equalsIgnoreCase("OPTIONS")) return(false);

		response.addHeader("Access-Control-Allow-Origin", cfg.AllowedOrigin);
		response.addHeader("Access-Control-Allow-Methods", cfg.AllowedMethods);
		response.addHeader("Access-Control-Allow-Headers", cfg.AllowedHeaders);
		response.addHeader("Access-Control-Allow-Credentials", "true");
		response.Status = 204; // success with no content
		
		return(true);
	}

	private void handleCORS(Response response) {
		
		if (cfg.AllowedOrigin == null) return;
		
		response.addHeader("Access-Control-Allow-Origin", cfg.AllowedOrigin);
	}
	// +---------+
	// | Helpers |
	// +---------+

	private void sendFileTo(File file, OutputStream outputStream) throws IOException {

		FileInputStream inputStream = null;

		try {
			inputStream = new FileInputStream(file);
			inputStream.transferTo(outputStream);
		}
		finally {
			if (inputStream != null) inputStream.close();
		}
	}

	// +---------+
	// | Members |
	// +---------+

	public static void main(String args[]) throws Exception{
		
		Config cfg = new Config();
			
		if (args.length > 1 && args[1].equalsIgnoreCase("secure")) {
			log.info("Running secure server");
			cfg.SSLCertificateFile = "@localhost.crt";
			cfg.SSLCertificateKeyFile = "@localhost.key";
		}

		final WebServer server = WebServer.create(cfg);
		server.registerStaticHandler("/", args[0], "text/plain");
		server.runSync();
	}

	protected Config cfg;
	protected HttpServer server;
	
	private ExecutorService pool;
	private OAuth2Login oauth2;
	private Encrypt cookieEncrypt;
	private PasswordStore passwordStore;

	private File staticPagesTemp; 

	private JsonParser jsonParser;
	
	private final static Logger log = Logger.getLogger(WebServer.class.getName());
}
