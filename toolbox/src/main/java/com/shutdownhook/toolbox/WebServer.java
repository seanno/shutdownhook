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
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class WebServer implements Closeable
{
	// +---------------+
	// | Configuration |
	// +---------------+
	
	public static class Config
	{
		public int Port = 7071;
		public int ThreadCount = 0; // 0 = use an on-demand pool, else specific fixed count
		public int ShutdownWaitSeconds = 30;
		public boolean ReturnExceptionDetails = false;

		// these aren't documented and are a bit problematic because they
		// get set as statics ... if you set them you'll change all instances
		// in the process. But without them clients can end up hanging sockets
		// for a long time / forever. Use 0 to leave system defaults untouched;
		// -1 for infinite; else seconds. 
		public int MaxReadSeconds = (60 * 3);
		public int MaxWriteSeconds = (60 * 3);

		// if these are set, will create a SecureServer instead of
		// a regular one. Parameters are named to match Apache config values
		// so hopefully they make sense to people.
		public String SSLCertificateFile;
		public String SSLCertificateKeyFile;

		// if non-empty, the provided config will be used to protect all routes
		public OAuth2Login.Config OAuth2;
		public String OAuth2CookieName = "SHOOK_OAUTH2";

		// optional directory holding static pages. Each .html or .js file in
		// this directory will be exposed as a static route at /basename.
		public String StaticPagesDirectory;
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
		public String Base;
		public String Path;
		public String Method;
		public String RemoteAddress;
		public String Referrer;
		public boolean Secure;
		public Map<String,String> QueryParams;
		public Map<String,String> Cookies;
		public Map<String,List<String>> Headers;
		public String Body;

		public Map<String,String> parseBodyAsQueryString() {
			return(Easy.parseQueryString(Body));
		}
	}

	// +----------+
	// | Response |
	// +----------+

	public static class Response {
		public int Status;
		public String Body;
		public File BodyFile;
		public String ContentType;
		public Map<String,String> Headers;

		public void setJson(String s) { Status = 200; Body = s; ContentType = "application/json"; }
		public void setText(String s) { Status = 200; Body = s; ContentType = "text/plain"; }
		public void setHtml(String s) { Status = 200; Body = s; ContentType = "text/html"; }
		public void setJS(String s) { Status = 200; Body = s; ContentType = "text/javascript"; }

		public void redirect(String url) {
			Status = 302;
			addHeader("Location", url);
		}

		public void setSessionCookie(String name, String val, Request request) {

			String cookie = name + "=" + Easy.urlEncode(val) + "; HttpOnly";
			if (request.Secure) cookie = cookie + "; Secure; SameSite=None";

			addHeader("Set-Cookie", cookie);
		}

		public void addHeader(String name, String val) {
			if (Headers == null) Headers = new HashMap<String,String>();
			Headers.put(name, val);
		}
	}

	// +------------------+
	// | WebServer Public |
	// +------------------+

	public static WebServer create(Config cfg) throws Exception {

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
				
				log.info(String.format("WebServer %s: %s",
									   handler.getClass().getName(),
									   exchange.getRequestURI()));

				Response response = new Response();
				response.Status = 200; // optimistic!
				
				try {
					Request request = setupRequest(exchange);
					if (!redirectForAuth(request, response)) handler.handle(request, response);
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
		if (cfg.OAuth2 != null) oauth2 = new OAuth2Login(cfg.OAuth2);
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

		if (response.Headers != null) {
			for (String name : response.Headers.keySet()) {
				exchange.getResponseHeaders().add(name, response.Headers.get(name));
			}
		}
		
		if (response.ContentType != null) {
			exchange.getResponseHeaders().add("Content-Type", response.ContentType);
		}

		if (response.BodyFile != null) {
			exchange.sendResponseHeaders(response.Status, response.BodyFile.length());
			sendFileTo(response.BodyFile, exchange.getResponseBody());
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

		Request request = new Request();

		setBaseUrl(exchange, request);
		
		request.Path = exchange.getRequestURI().toString();
		request.Method = exchange.getRequestMethod();
		request.RemoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
		request.Referrer = exchange.getRequestHeaders().getFirst("Referer");
		request.Headers = exchange.getRequestHeaders();

		// query string
		request.QueryParams = Easy.parseQueryString(exchange.getRequestURI().getRawQuery());

		// cookies
		request.Cookies = new HashMap<String,String>();
		List<String> cookies = exchange.getRequestHeaders().get("Cookie");

		if (cookies != null) {
			for (String cookieList : cookies) {
				for (String cookie : cookieList.split(";")) {
					String[] nv = cookie.split("=");
					if (nv.length != 2) continue;
					try {
						request.Cookies.put(nv[0].trim(), Easy.urlDecode(nv[1].trim()));
					} catch (Exception e) {
						// we encode all of ours so just ignore this
					}
				}
			}
		}
		
		// body
		if (request.Method.equalsIgnoreCase("POST") ||
			request.Method.equalsIgnoreCase("PUT")) {
			
			InputStream stream = null;

			try {
				stream = exchange.getRequestBody();
				request.Body = Easy.stringFromInputStream(stream);
			}
			finally {
				if (stream != null) stream.close();
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
		
		if (cfg.StaticPagesDirectory == null) {
			return;
		}

		File routesDir = new File(cfg.StaticPagesDirectory);
		for (File htmlFile : routesDir.listFiles()) {

			String name = htmlFile.getName();
			int ichLastDot = name.lastIndexOf(".");

			if (ichLastDot != -1) {

				String ext = name.substring(ichLastDot);
				String base = name.substring(0, ichLastDot);

				boolean isHtml = ext.equalsIgnoreCase(".html");
				boolean isJs = ext.equalsIgnoreCase(".js");
				
				if (isHtml || isJs) {

					registerStaticHandler("/" + base + (isHtml ? "" : ".js"),
										  Easy.stringFromFile(htmlFile.getAbsolutePath()),
										  "text/" + (isHtml ? "html" : "javascript"));
				}
			}
		}
	}

	// +--------+
	// | OAuth2 |
	// +--------+

	private void registerAuth() {
		
		if (oauth2 == null) return;

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

				response.setSessionCookie(cfg.OAuth2CookieName, state.dehydrate(), request);

				// nyi - redirect somewhere
				// nyi - redirect somewhere
				// nyi - redirect somewhere
				// nyi - redirect somewhere
				// nyi - redirect somewhere
				response.setText("DID IT! " + state.dehydrate());
			}
		});
		
	}
	
	private boolean redirectForAuth(Request request, Response response) {

		// quick exits

		if (oauth2 == null) return(false);
		if (request.Path.startsWith(cfg.OAuth2.RedirectPath)) return(false);

		// already logged in?
		
		OAuth2Login.State state = getOAuth2State(request);
		if (state.isAuthenticated()) return(false);

		// ok, fine ... redirect to auth url
		String url = oauth2.getAuthenticationURL(request.Base, state);
		response.setSessionCookie(cfg.OAuth2CookieName, state.dehydrate(), request);
		response.redirect(url);

		return(true);
	}

	private OAuth2Login.State getOAuth2State(Request request) {
		String dehydrated = request.Cookies.get(cfg.OAuth2CookieName);
		return(OAuth2Login.State.rehydrate(dehydrated));
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
	
	private final static Logger log = Logger.getLogger(WebServer.class.getName());
}
