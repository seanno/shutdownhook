/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.toolbox;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.InterruptedException;
import java.lang.Runtime;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
		public String Url;
		public String Method;
		public Map<String,String> QueryParams;
		public String Body;
	}

	// +----------+
	// | Response |
	// +----------+

	public static class Response {
		public int Status;
		public String Body;
		public String ContentType;

		public void setJson(String s) { Status = 200; Body = s; ContentType = "application/json"; }
		public void setText(String s) { Status = 200; Body = s; ContentType = "text/plain"; }
		public void setHtml(String s) { Status = 200; Body = s; ContentType = "text/html"; }
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

		return(server);
		
	}

	public void start() {
		server.start();
		log.info("WebServer listening for requests on port " + Integer.toString(cfg.Port));
	}
	
	public void close() {

		log.info("WebServer Shutting down");

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
					handler.handle(request, response);
				}
				catch (Exception e) {
					
					response.Status = 500;
					response.Body = null;
					response.ContentType = null;

					String msg = String.format("Exception in %s: %s",
											   handler.getClass().getName(), e);

					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					String stack = sw.toString();

					log.severe(msg);
					log.severe(stack);

					if (cfg.ReturnExceptionDetails) {
						response.Body = msg + "\n\n" + stack;
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

	public WebServer(Config cfg) {
		this.cfg = cfg;
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

		if (response.ContentType != null) {
			exchange.getResponseHeaders().add("Content-Type", response.ContentType);
		}

		if (response.Body == null || response.Body.isEmpty()) {
			exchange.sendResponseHeaders(response.Status, -1);
		}
		else {
			byte[] rgb = response.Body.getBytes();
			exchange.sendResponseHeaders(response.Status, rgb.length);
			exchange.getResponseBody().write(rgb);
		}
	}

	private Request setupRequest(HttpExchange exchange) throws IOException {

		Request request = new Request();

		request.Url = exchange.getRequestURI().toString();
		request.Method = exchange.getRequestMethod();

		request.QueryParams = new HashMap<String,String>();

		String queryString = exchange.getRequestURI().getRawQuery();

		if (queryString != null && !queryString.isEmpty()) {
			for (String pair : queryString.split("&")) {
				String[] kv = pair.split("=");
				try {
					request.QueryParams.put(URLDecoder.decode(kv[0], "UTF-8"),
											URLDecoder.decode(kv[1], "UTF-8"));
				}
				catch (UnsupportedEncodingException e) {
					// will never happen
				}
			}
		}

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
	
	private final static Logger log = Logger.getLogger(WebServer.class.getName());
}
